package be.lomagnette.a2a.ironram.garage;

import be.lomagnette.a2a.telemetry.MissionDashboard;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class Baarvis {

    private static final AtomicLong SEQ = new AtomicLong();

    @Inject
    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @Tool(name = "Baarvis", description = "Can find the object and their location based on some key words")
    @Transactional
    public List<KeyObject> findObjects(@ToolArg(description = "Keywords to search in object descriptions") List<String> keywords) {
        var span = MissionDashboard.tool("baarvis-" + SEQ.incrementAndGet(), "ironRam", "Baarvis", "http://localhost:" + port + "/mcp")
                .input("keywords: " + String.join(", ", keywords)).start();

        List<KeyObject> found = KeyObject.findByDescriptionContainingAllWords(keywords);

        span.ok("found " + found.size() + " object(s)");
        return found;
    }
}
