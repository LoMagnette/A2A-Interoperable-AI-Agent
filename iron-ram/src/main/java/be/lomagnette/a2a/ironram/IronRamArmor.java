package be.lomagnette.a2a.ironram;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class IronRamArmor {



    @Tool(name="collect", value="Navigate through the universe to a specific destination")
    @Transactional
    public KeyObject navigateAndCollect(@P("destination") String destination, @P("name") String name) {
        Log.info("navigated to " + destination);
        var object = KeyObject.findByName(name);
        var objectName = object == null ? null : object.name;
        Log.info("object to collect " + objectName);
        return object;
    }
}
