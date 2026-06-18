package be.lomagnette.a2a.telemetry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fire-and-forget telemetry client for the live "Mission Control" dashboard.
 *
 * <p>Each process in the demo (Nick Wooly, Iron-Ram, the Garage MCP server and
 * Bruce Baaner) reports its own activity here. Events are posted asynchronously
 * on a single daemon thread and <strong>every failure is swallowed</strong> so
 * instrumentation can never slow down or break the mission — if the dashboard
 * is not running, the demo simply runs without it.
 *
 * <p>The dashboard location is resolved from (in order) the {@code dashboard.url}
 * system property, the {@code DASHBOARD_URL} environment variable, then the
 * default {@code http://localhost:8090}.
 *
 * <p>Typical usage:
 * <pre>{@code
 * MissionDashboard.event("tool-start")
 *         .id("collect-3").parent("ironRam").label("collect: Space Stone")
 *         .kind("mcp").endpoint("http://localhost:8082/mcp")
 *         .detail("navigate to Vormir").send();
 * }</pre>
 */
public final class MissionDashboard {

    private static final String BASE_URL = resolveBaseUrl();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .build();

    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mission-dashboard-emitter");
        t.setDaemon(true);
        return t;
    });

    private MissionDashboard() {
    }

    private static String resolveBaseUrl() {
        String url = System.getProperty("dashboard.url");
        if (url == null || url.isBlank()) {
            url = System.getenv("DASHBOARD_URL");
        }
        if (url == null || url.isBlank()) {
            url = "http://localhost:8090";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Start building an event of the given type (e.g. {@code agent-start}). */
    public static Event event(String type) {
        return new Event(type);
    }

    /**
     * Block briefly so queued events are delivered before a short-lived process
     * exits (e.g. Nick Wooly's {@code main}). Best-effort; never throws. After
     * this call no further events can be sent.
     */
    public static void flush() {
        SENDER.shutdown();
        try {
            SENDER.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Fluent builder for a single dashboard event. */
    public static final class Event {

        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Event(String type) {
            // "type" is always emitted first with no whitespace so the dashboard
            // server can cheaply detect control events such as mission-start.
            fields.put("type", type);
        }

        /** Stable id of the node this event refers to (matches start/end pairs). */
        public Event id(String v) {
            return put("id", v);
        }

        /** Id of the parent node, used to nest e.g. MCP tool calls under Iron-Ram. */
        public Event parent(String v) {
            return put("parent", v);
        }

        /** Human-readable name shown on the card / row. */
        public Event label(String v) {
            return put("label", v);
        }

        /** Node kind: {@code orchestrator}, {@code llm}, {@code a2a} or {@code mcp}. */
        public Event kind(String v) {
            return put("kind", v);
        }

        /** Where the work runs (URL or model name), shown as a sub-label. */
        public Event endpoint(String v) {
            return put("endpoint", v);
        }

        /** Outcome for {@code *-end} events: {@code ok} or {@code error}. */
        public Event status(String v) {
            return put("status", v);
        }

        /** Free-form detail (input, output or error message); truncated for the wire. */
        public Event detail(String v) {
            return put("detail", truncate(v));
        }

        private Event put(String key, String value) {
            if (value != null) {
                fields.put(key, value);
            }
            return this;
        }

        /** Fire the event asynchronously. Never blocks the caller, never throws. */
        public void send() {
            final String json = toJson(fields);
            try {
                SENDER.submit(() -> post(json));
            } catch (RuntimeException ignored) {
                // executor rejected (shutdown) — nothing we can or should do
            }
        }
    }

    private static void post(String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/event"))
                    .timeout(Duration.ofMillis(800))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // best-effort: the dashboard is optional and must never break the mission
        }
    }

    private static String truncate(String v) {
        if (v == null) {
            return null;
        }
        String s = v.strip();
        return s.length() > 600 ? s.substring(0, 600) + "…" : s;
    }

    private static String toJson(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"")
              .append(escape(String.valueOf(e.getValue()))).append('"');
        }
        return sb.append('}').toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
