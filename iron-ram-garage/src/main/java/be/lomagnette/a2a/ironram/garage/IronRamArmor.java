package be.lomagnette.a2a.ironram.garage;

import be.lomagnette.a2a.telemetry.MissionDashboard;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class IronRamArmor {

    private static final AtomicLong SEQ = new AtomicLong();

    @Tool(name = "collect", description = "Navigate through the universe to a specific destination and collect the named object")
    @Transactional
    public KeyObject navigateAndCollect(
            @ToolArg(description = "Destination to navigate to") String destination,
            @ToolArg(description = "Name of the object to collect") String name) {
        String nodeId = "collect-" + SEQ.incrementAndGet();
        MissionDashboard.event("tool-start")
                .id(nodeId).parent("ironRam").label("collect: " + name).kind("mcp")
                .endpoint("http://localhost:8082/mcp")
                .detail("navigate → " + destination).send();
        try {
            Log.info("navigated to " + destination);
            var object = KeyObject.findByName(name);
            Log.info("object to collect " + object);
            if (object == null) {
                throw new IllegalArgumentException(
                        "No object named '" + name + "' exists at destination '" + destination + "'");
            }
            MissionDashboard.event("tool-end")
                    .id(nodeId).status("ok").detail("collected '" + name + "'").send();
            return object;
        } catch (RuntimeException e) {
            MissionDashboard.event("tool-end")
                    .id(nodeId).status("error").detail(String.valueOf(e.getMessage())).send();
            throw e;
        }
    }
}
