package be.lomagnette.a2a.ironram.garage;

import be.lomagnette.a2a.telemetry.MissionDashboard;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class Baarvis {

    private static final AtomicLong SEQ = new AtomicLong();

    @Tool(name = "Baarvis", description = "Can find the object and their location based on some key words")
    @Transactional
    public List<KeyObject> findObjects(@ToolArg(description = "Keywords to search in object descriptions") List<String> keywords) {
        String nodeId = "baarvis-" + SEQ.incrementAndGet();
        MissionDashboard.event("tool-start")
                .id(nodeId).parent("ironRam").label("Baarvis").kind("mcp")
                .endpoint("http://localhost:8082/mcp")
                .detail("keywords: " + String.join(", ", keywords)).send();
        try {
            List<KeyObject> result = KeyObject.findByDescriptionContainingAllWords(keywords);
            MissionDashboard.event("tool-end")
                    .id(nodeId).status("ok")
                    .detail("found " + result.size() + " object(s)").send();
            return result;
        } catch (RuntimeException e) {
            MissionDashboard.event("tool-end")
                    .id(nodeId).status("error").detail(String.valueOf(e.getMessage())).send();
            throw e;
        }
    }
}
