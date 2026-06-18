package be.lomagnette.a2a.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * "Mission Control" — a tiny, dependency-free HTTP server that powers the live
 * dashboard for the A2A + MCP demo.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /}        — the dashboard page (served from {@code dashboard.html}).</li>
 *   <li>{@code GET  /events}  — Server-Sent Events stream of mission activity.</li>
 *   <li>{@code POST /event}   — ingest a single JSON event (called by every agent/tool).</li>
 *   <li>{@code POST /reset}   — clear the current run.</li>
 * </ul>
 *
 * <p>Run it once at the start of the demo and leave it running; it survives many
 * mission runs. A connecting browser is replayed the buffered events for the
 * current run, so it always shows a consistent picture even if it joins late.
 */
public final class DashboardServer {

    private static final int PORT = resolvePort();
    private static final int MAX_BUFFER = 5000;

    /** Events for the current run, replayed to newly-connected browsers. */
    private static final List<String> buffer = new CopyOnWriteArrayList<>();
    /** Connected SSE clients. */
    private static final List<Client> clients = new CopyOnWriteArrayList<>();

    private DashboardServer() {
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "dashboard-http");
            t.setDaemon(false);
            return t;
        }));

        server.createContext("/events", new EventsHandler());
        server.createContext("/event", new IngestHandler());
        server.createContext("/reset", new ResetHandler());
        server.createContext("/", new RootHandler());

        server.start();
        System.out.println("──────────────────────────────────────────────────────");
        System.out.println("  Mission Control dashboard is live");
        System.out.println("    →  http://localhost:" + PORT + "/");
        System.out.println("  Leave this running, then start the agents + Nick Wooly.");
        System.out.println("──────────────────────────────────────────────────────");
    }

    private static int resolvePort() {
        Integer prop = Integer.getInteger("dashboard.port");
        if (prop != null) {
            return prop;
        }
        String env = System.getenv("DASHBOARD_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return 8090;
    }

    // ----------------------------------------------------------------- ingest

    private static final class IngestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, "text/plain", "method not allowed");
                return;
            }

            String body;
            try (InputStream in = ex.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }

            if (!body.isEmpty()) {
                // A new mission resets the current run so late joiners see only it.
                if (body.contains("\"type\":\"mission-start\"")) {
                    buffer.clear();
                }
                buffer.add(body);
                while (buffer.size() > MAX_BUFFER) {
                    buffer.remove(0);
                }
                broadcast(body);
            }
            respond(ex, 204, "text/plain", "");
        }
    }

    // ------------------------------------------------------------------ reset

    private static final class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex)) {
                return;
            }
            buffer.clear();
            broadcast("{\"type\":\"reset\"}");
            respond(ex, 204, "text/plain", "");
        }
    }

    // --------------------------------------------------------------- SSE feed

    private static final class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            ex.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().add("Cache-Control", "no-cache, no-transform");
            ex.getResponseHeaders().add("Connection", "keep-alive");
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, 0); // 0 => chunked, open-ended stream

            Client client = new Client();
            clients.add(client);
            OutputStream os = ex.getResponseBody();
            try {
                writeFrame(os, ": connected\n\n");
                // Replay the current run so a late-joining browser is consistent.
                for (String evt : buffer) {
                    writeFrame(os, "data: " + evt + "\n\n");
                }
                // Stream live events; emit a heartbeat on idle to detect disconnects.
                while (true) {
                    String evt = client.queue.poll(15, TimeUnit.SECONDS);
                    if (evt == null) {
                        writeFrame(os, ": ping\n\n");
                    } else {
                        writeFrame(os, "data: " + evt + "\n\n");
                    }
                }
            } catch (IOException | InterruptedException disconnect) {
                // client went away — fall through to cleanup
            } finally {
                clients.remove(client);
                ex.close();
            }
        }

        private void writeFrame(OutputStream os, String frame) throws IOException {
            os.write(frame.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }

    /** One connected browser, fed through a bounded queue. */
    private static final class Client {
        final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(10_000);
    }

    private static void broadcast(String event) {
        for (Client c : clients) {
            // Drop for a hopelessly-backed-up client rather than blocking ingest.
            c.queue.offer(event);
        }
    }

    // -------------------------------------------------------------- dashboard

    private static final class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex)) {
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (!"/".equals(path) && !"/index.html".equals(path)) {
                respond(ex, 404, "text/plain", "not found");
                return;
            }
            byte[] html = loadResource("/dashboard.html");
            if (html == null) {
                respond(ex, 500, "text/plain", "dashboard.html missing from classpath");
                return;
            }
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, html.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html);
            }
        }
    }

    private static byte[] loadResource(String name) {
        try (InputStream in = DashboardServer.class.getResourceAsStream(name)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    // ----------------------------------------------------------------- shared

    /** Answer CORS pre-flight requests. Returns true if the request was handled. */
    private static boolean handlePreflight(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private static void respond(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", contentType + "; charset=utf-8");
        if (bytes.length == 0) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
