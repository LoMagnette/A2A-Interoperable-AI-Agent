package be.lomagnette.a2a.wooly;

import be.lomagnette.a2a.telemetry.MissionDashboard;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.HtmlReportGenerator;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the "restore the universe" mission: Nick Wooly's LLM names the objects,
 * Iron-Ram collects them over A2A (calling the Garage MCP tools), and Bruce
 * snaps. Triggered on demand from the dashboard's launch button.
 */
@ApplicationScoped
public class MissionService {

    private static final String MISSION = """
            BaaNos just destroy half the universe using the infinity stones.
            The only way to reverse it is to quickly collect the infinity stones and snap it.
            """;
    public static final String LLM_URL = "http://localhost:11434";
    public static final String MODEL_NAME = "gemma4";
    public static final String RESULT = "Result";

    public static class ObjectToCollect implements TypedKey<String>{};
    public static class Stones implements TypedKey<String>{};
    public static class Mission implements TypedKey<String>{};


    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService runner = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mission-runner");
        t.setDaemon(true);
        return t;
    });

    /** Launch the mission in the background. Returns false if one is already running. */
    public boolean launch() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        runner.submit(() -> {
            try {
                runMission();
            } catch (Exception e) {
                Log.error("Mission failed", e);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    private void runMission() {
        // Nick Wooly's LLM runs locally, in this orchestrator process. A chat
        // model listener reports its request/response to Mission Control without
        // touching the orchestration code below.
        var nickSpan = MissionDashboard.llm("nickWooly", "Nick Wooly", "ollama/" + MODEL_NAME)
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
                .baseUrl(LLM_URL)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .modelName(MODEL_NAME)
                .listeners(List.of(dashboardListener))
                .build();

        var monitor = new AgentMonitor();

        var nickWooly = AgenticServices
                .agentBuilder(NickWooly.class)
                .chatModel(model)
                .outputKey(ObjectToCollect.class)
                .build();

        var ironRam = AgenticServices
                .a2aBuilder("http://localhost:8080", IronRam.class)
                .outputKey("Stones")
                .build();

        var bruce = AgenticServices
                .a2aBuilder("http://localhost:8081", Bruce.class)
                .outputKey(RESULT)
                .build();

        Avengers avengers = AgenticServices
                .plannerBuilder(Avengers.class)
                .subAgents(nickWooly, ironRam, bruce)
                .outputKey(RESULT)
                .planner(GoalOrientedPlanner::new)
                .listener(monitor)
                .build();

        var missionSpan = MissionDashboard.mission("mission", "Restore the universe")
                .input(MISSION).start();
        try {
            var result = avengers.assemble(MISSION);
            missionSpan.ok(String.valueOf(result));
            Log.info("Mission result: " + result);
        } catch (RuntimeException e) {
            missionSpan.error(String.valueOf(e.getMessage()));
            throw e;
        }

        HtmlReportGenerator.generateReport(monitor, Path.of("a2a-workflow.html"));
    }

    public interface IronRam {
        @Agent
        String collect(@K(ObjectToCollect.class) String keywords);
    }

    public interface Bruce {
        @Agent
        String snap(@K(Stones.class) String stones);
    }

    public interface Avengers {
        @Agent("Avenger assemble and restore the universe")
        String assemble(@K(Mission.class) String mission);
    }
}
