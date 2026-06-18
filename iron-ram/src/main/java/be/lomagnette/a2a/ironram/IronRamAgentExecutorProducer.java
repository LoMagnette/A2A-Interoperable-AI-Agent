package be.lomagnette.a2a.ironram;


import be.lomagnette.a2a.telemetry.MissionDashboard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.*;

import java.util.List;


@ApplicationScoped
public final class IronRamAgentExecutorProducer {

    private final IronRam ironRam;

    public IronRamAgentExecutorProducer(IronRam ironRam) {
        this.ironRam = ironRam;
    }

    /**
     * Creates the agent executor for the content writer agent.
     *
     * @return the agent executor
     */
    @Produces
    public AgentExecutor agentExecutor() {
        return new SuperHeroExecutor(ironRam);
    }

    /**
     * Agent executor implementation for content writer.
     */
    private static class SuperHeroExecutor implements AgentExecutor {

        private final IronRam agent;

        SuperHeroExecutor(final IronRam ironRam) {
            this.agent = ironRam;
        }

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {

            if(context.getMessage() == null){
                emitter.reject();
            }
            // mark the task as submitted and start working on it
            if (context.getTask() == null) {
                emitter.submit();
            }
            emitter.startWork();

            // extract the text from the message
            final String assignment = extractTextFromMessage(context.getMessage());

            var contextId = context.getContextId();

            MissionDashboard.event("agent-start")
                    .id("ironRam").label("Iron-Ram").kind("a2a")
                    .endpoint("http://localhost:8080").detail(assignment).send();

            // call the content writer agent with the message
            final String response;
            try {
                response = agent.collect(contextId, assignment).toString();
            } catch (RuntimeException e) {
                MissionDashboard.event("agent-end")
                        .id("ironRam").status("error")
                        .detail(String.valueOf(e.getMessage())).send();
                throw e;
            }

            MissionDashboard.event("agent-end")
                    .id("ironRam").status("ok").detail(response).send();

            // create the response part
            final TextPart responsePart = new TextPart(response, null);
            final List<Part<?>> parts = List.of(responsePart);

            // add the response as an artifact and complete the task
            emitter.addArtifact(parts, null, null, null);
            emitter.complete();
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            final Task task = context.getTask();

            if (task.status().state() == TaskState.TASK_STATE_CANCELED) {
                // task already cancelled
                throw new TaskNotCancelableError();
            }

            if (task.status().state() == TaskState.TASK_STATE_COMPLETED) {
                // task already completed
                throw new TaskNotCancelableError();
            }

            // cancel the task
            emitter.cancel();
        }


        private String extractTextFromMessage(final Message message) {
            final StringBuilder textBuilder = new StringBuilder();
            for (final Part part : message.parts()) {
                if (part instanceof TextPart textPart) {
                    textBuilder.append(textPart.text());
                }
            }
            return textBuilder.toString();
        }
    }
}
