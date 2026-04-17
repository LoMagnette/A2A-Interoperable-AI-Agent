package be.lomagnette.a2a.baaner;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.tasks.AgentEmitter;
import io.a2a.spec.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

/**
 * Producer for Content Writer Agent Executor.
 */
@ApplicationScoped
public final class BruuceAgentExecutorProducer {

    /**
     * The content writer agent instance.
     */
    private final BruceBaaner bruceBaaner;

    public BruuceAgentExecutorProducer(BruceBaaner bruceBaaner) {
        this.bruceBaaner = bruceBaaner;
    }

    /**
     * Creates the agent executor for the content writer agent.
     *
     * @return the agent executor
     */
    @Produces
    public AgentExecutor agentExecutor() {
        return new ContentWriterAgentExecutor(bruceBaaner);
    }

    private static class ContentWriterAgentExecutor implements AgentExecutor {

        private final BruceBaaner agent;


        ContentWriterAgentExecutor(final BruceBaaner baaner) {
            this.agent = baaner;
        }

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            if(context.getMessage() == null){
                emitter.reject();
            }

            if (context.getTask() == null) {
                emitter.submit();
            }
            emitter.startWork();

            // extract the text from the message
            final String assignment = extractTextFromMessage(context.getMessage());

            // call the content writer agent with the message

            try {
                var response = agent.snap(assignment);
                // create the response part
                final TextPart responsePart = new TextPart(response, null);
                final List<Part<?>> parts = List.of(responsePart);

                // add the response as an artifact and complete the task
                emitter.addArtifact(parts, null, null, null);
                emitter.complete();

            } catch (Exception e) {
                final TextPart responsePart = new TextPart("""
                    Bruce Baaner was not able to snap and restore the universe and in an
                    excess of rage transform into HULK and killed all the hero on earth
                    then join Baanos.
                """, null);
                final List<Part<?>> parts = List.of(responsePart);
                emitter.addArtifact(parts, null, null, null);
                emitter.fail();
            }
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
            if (message.parts() != null) {
                for (final Part part : message.parts()) {
                    if (part instanceof TextPart textPart) {
                        textBuilder.append(textPart.text());
                    }
                }
            }
            return textBuilder.toString();
        }
    }
}
