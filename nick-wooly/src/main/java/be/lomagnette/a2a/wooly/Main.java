package be.lomagnette.a2a.wooly;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.V;

import java.util.Map;


public class Main {
    void main() {
        ChatModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .modelName("granite4:latest")
                .build();

        var nickWooly = AgenticServices
                .agentBuilder(NickWooly.class)
                .chatModel(model)
                .outputKey("object")
                .build();

        var ironRam = AgenticServices
                .a2aBuilder("http://localhost:8080", IronRam.class)
                .inputKeys("object")
                .outputKey("stones")
                .build();

         var bruce = AgenticServices
                 .a2aBuilder("http://localhost:8081", Bruce.class)
                 .inputKeys("stones")
                 .outputKey("result")
                 .build();


        var executeMission = AgenticServices.sequenceBuilder()
                .subAgents(nickWooly, ironRam, bruce)
                .outputKey("result")
                .build();

        Object invoke = executeMission.invoke(Map.of("mission", """
                BaaNos just destroy half the universe using the infinity stones.
                The only way to reverse it is to quickly collect the infinity stones and snap it.
                """));
        System.out.println("-------- Mission results ---------");
        System.out.println(invoke);
    }

    public interface IronRam {

        @Agent
        String collect(@V("object") String keywords);
    }

    public interface Bruce {
        @Agent
        String snap(@V("stones") String stones);
    }

}
