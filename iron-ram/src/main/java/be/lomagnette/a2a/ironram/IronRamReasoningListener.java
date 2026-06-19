package be.lomagnette.a2a.ironram;

import be.lomagnette.a2a.telemetry.MissionDashboard;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Reports Iron-Ram's own model reasoning to Mission Control.
 *
 * <p>Iron-Ram is itself an AI service: when it receives an A2A task it runs the
 * gemma4 model, which decides <em>which</em> Garage MCP tools to call, then runs
 * again with the tool results to produce its final answer. The tool-deciding
 * rounds are reported here as steps nested under the {@code ironRam} node, so the
 * dashboard no longer shows a blank between the incoming A2A request and the
 * first MCP tool call.
 *
 * <p>Only rounds that actually request tools are reported. The last round
 * produces no tool calls — just the answer Iron-Ram returns over A2A — and that
 * answer is already shown as the task artifact, so reporting it would duplicate
 * it on the wire.
 *
 * <p>Quarkus auto-registers every {@link ChatModelListener} CDI bean onto the
 * application's chat model, so no wiring is needed in the AI service itself.
 */
@ApplicationScoped
public class IronRamReasoningListener implements ChatModelListener {

    private static final AtomicInteger ROUND = new AtomicInteger();

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        AiMessage ai = ctx.chatResponse().aiMessage();
        if (!ai.hasToolExecutionRequests()) {
            return;   // final answer round — shown as the A2A artifact, don't duplicate
        }
        int round = ROUND.incrementAndGet();
        MissionDashboard.reasoning("ironram-llm-" + round, "ironRam",
                        "reasoning #" + round, "ollama/gemma4")
                .start()
                .ok(describe(ai));
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        int round = ROUND.incrementAndGet();
        MissionDashboard.reasoning("ironram-llm-err-" + round, "ironRam",
                        "reasoning #" + round, "ollama/gemma4")
                .start()
                .error(String.valueOf(ctx.error().getMessage()));
    }

    /** The tool calls the model decided to make this round. */
    private static String describe(AiMessage ai) {
        return "decided to call: " + ai.toolExecutionRequests().stream()
                .map(t -> t.name() + "(" + t.arguments() + ")")
                .collect(Collectors.joining(", "));
    }
}
