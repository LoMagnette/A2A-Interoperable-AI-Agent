package be.lomagnette.a2a.ironram.garage;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class Baarvis {

    @Tool(name = "Baarvis", description = "Can find the object and their location based on some key words")
    @Transactional
    public List<KeyObject> findObjects(@ToolArg(description = "Keywords to search in object descriptions") List<String> keywords) {
        return KeyObject.findByDescriptionContainingAllWords(keywords);
    }
}
