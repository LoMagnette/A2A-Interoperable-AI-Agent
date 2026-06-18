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
        ChatModelListener dashboardListener = new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext ctx) {
                MissionDashboard.event("agent-start")
                        .id("nickWooly").label("Nick Wooly").kind("llm")
                        .endpoint("ollama/gemma4")
                        .detail("Identifying which objects the mission needs").send();
            }

            @Override
            public void onResponse(ChatModelResponseContext ctx) {
                MissionDashboard.event("agent-end")
                        .id("nickWooly").status("ok")
                        .detail(ctx.chatResponse().aiMessage().text()).send();
            }

            @Override
            public void onError(ChatModelErrorContext ctx) {
                MissionDashboard.event("agent-end")
                        .id("nickWooly").status("error")
                        .detail(String.valueOf(ctx.error().getMessage())).send();
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

        MissionDashboard.event("mission-start").id("mission")
                .label("Restore the universe").detail(mission).send();

        Object invoke;
        try {
            invoke = executeMission.invoke(Map.of("mission", mission));
            MissionDashboard.event("mission-end").id("mission")
                    .status("ok").detail(String.valueOf(invoke)).send();
        } catch (RuntimeException e) {
            MissionDashboard.event("mission-end").id("mission")
                    .status("error").detail(String.valueOf(e.getMessage())).send();
            throw e;
        }

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
