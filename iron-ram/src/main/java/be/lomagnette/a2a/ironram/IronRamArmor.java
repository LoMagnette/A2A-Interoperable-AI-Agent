package be.lomagnette.a2a.ironram;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.V;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class IronRamArmor {



    @Tool("Navigate through the universe to a specific destination")
    @Transactional
    public KeyObject navigateAndCollect(@V("Destination to navigate to")String destination, @V("Object name") String name) {
        Log.info("navigated to " + destination);
        var object = KeyObject.findByName(name);
        var objectName = object == null ? null : object.name;
        Log.info("object to collect " + objectName);
        return object;
    }
}
