package be.lomagnette.a2a.ironram.garage;

import be.lomagnette.a2a.telemetry.MissionDashboard;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class IronRamArmor {

    private static final AtomicLong SEQ = new AtomicLong();

    @Inject
    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @Tool(name = "collect", description = "Navigate through the universe to a specific destination and collect the named object")
    @Transactional
    public KeyObject navigateAndCollect(
            @ToolArg(description = "Destination to navigate to") String destination,
            @ToolArg(description = "Name of the object to collect") String name) {
        var span = MissionDashboard.tool("collect-" + SEQ.incrementAndGet(), "ironRam", "collect: " + name, "http://localhost:" + port + "/mcp")
                .input("navigate → " + destination).start();

        Log.info("navigated to " + destination);
        var object = KeyObject.findByName(name);
        Log.info("object to collect " + object);
        if (object == null) {
            span.error("no object named '" + name + "' at '" + destination + "'");
            throw new IllegalArgumentException(
                    "No object named '" + name + "' exists at destination '" + destination + "'");
        }

        span.ok("collected '" + name + "'");
        return object;
    }
}
