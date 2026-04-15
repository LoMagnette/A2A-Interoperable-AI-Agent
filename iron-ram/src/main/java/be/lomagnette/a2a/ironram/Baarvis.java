package be.lomagnette.a2a.ironram;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.V;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class Baarvis {

    @Tool("Can find the object and their location based on their some key words")
    @Transactional
    public List<KeyObject> findObjects(@V("keywords") List<String> keywords) {
        var items  = KeyObject.findByDescriptionContainingAllWords(keywords);
        return items;
    }
}
