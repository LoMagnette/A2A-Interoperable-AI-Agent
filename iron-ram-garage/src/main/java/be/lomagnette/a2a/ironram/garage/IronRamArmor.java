package be.lomagnette.a2a.ironram.garage;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class IronRamArmor {

    @Tool(name = "collect", description = "Navigate through the universe to a specific destination and collect the named object")
    @Transactional
    public KeyObject navigateAndCollect(
            @ToolArg(description = "Destination to navigate to") String destination,
            @ToolArg(description = "Name of the object to collect") String name) {
        Log.info("navigated to " + destination);
        var object = KeyObject.findByName(name);
        Log.info("object to collect " + object);
        if (object == null) {
            throw new IllegalArgumentException(
                    "No object named '" + name + "' exists at destination '" + destination + "'");
        }
        return object;
    }
}
