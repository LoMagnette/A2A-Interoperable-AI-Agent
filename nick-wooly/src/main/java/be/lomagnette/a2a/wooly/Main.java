package be.lomagnette.a2a.wooly;

import be.lomagnette.a2a.telemetry.MissionDashboard;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.V;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public class Main {
    void main() {

        var monitor = new AgentMonitor();

        // Report Nick Wooly's LLM step (identifyMission) to the live dashboard.
        var nickSpan = MissionDashboard.llm("nickWooly", "Nick Wooly", "ollama/gemma4")
                .input("Identifying which objects the mission needs");
        ChatModelListener dashboardListener = new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext ctx) {
                nickSpan.start();
            }

            @Override
            public void onResponse(ChatModelResponseContext ctx) {
                nickSpan.ok(ctx.chatResponse().aiMessage().text());
            }

            @Override
            public void onError(ChatModelErrorContext ctx) {
                nickSpan.error(String.valueOf(ctx.error().getMessage()));
            }
        };

        ChatModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .modelName("gemma4")
                .listeners(List.of(dashboardListener))
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
                .listener(monitor)
                .build();

        String mission = """
                BaaNos just destroy half the universe using the infinity stones.
                The only way to reverse it is to quickly collect the infinity stones and snap it.
                """;

        var missionSpan = MissionDashboard.mission("mission", "Restore the universe")
                .input(mission).start();

        Object invoke = executeMission.invoke(Map.of("mission", mission));
        missionSpan.ok(String.valueOf(invoke));

        System.out.println("-------- Mission results ---------");
        System.out.println(invoke);

        HtmlReportGenerator.generateReport(monitor, Path.of("a2a-workflow.html"));

        // Let the final dashboard events drain before the JVM exits.
        MissionDashboard.flush();
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
