package be.lomagnette.a2a.baaner;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

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
        public void execute(final RequestContext context,
                            final EventQueue eventQueue) throws JSONRPCError {
            final TaskUpdater updater = new TaskUpdater(context, eventQueue);

            // mark the task as submitted and start working on it
            if (context.getTask() == null) {
                updater.submit();
            }
            updater.startWork();

            // extract the text from the message
            final String assignment = extractTextFromMessage(context.getMessage());

            // call the content writer agent with the message

            try {
                var response = agent.snap(assignment);
                // create the response part
                final TextPart responsePart = new TextPart(response, null);
                final List<Part<?>> parts = List.of(responsePart);

                // add the response as an artifact and complete the task
                updater.addArtifact(parts, null, null, null);
                updater.complete();

            } catch (Exception e) {
                final TextPart responsePart = new TextPart("""
                    Bruce Baaner was not able to snap and restore the universe and in an
                    excess of rage transform into HULK and killed all the hero on earth
                    then join Baanos.
                """, null);
                final List<Part<?>> parts = List.of(responsePart);
                updater.addArtifact(parts, null, null, null);
                updater.fail();
            }

        }

        private String extractTextFromMessage(final Message message) {
            final StringBuilder textBuilder = new StringBuilder();
            if (message.getParts() != null) {
                for (final Part part : message.getParts()) {
                    if (part instanceof TextPart textPart) {
                        textBuilder.append(textPart.getText());
                    }
                }
            }
            return textBuilder.toString();
        }

        @Override
        public void cancel(final RequestContext context,
                           final EventQueue eventQueue) throws JSONRPCError {
            final Task task = context.getTask();

            if (task.getStatus().state() == TaskState.CANCELED) {
                // task already cancelled
                throw new TaskNotCancelableError();
            }

            if (task.getStatus().state() == TaskState.COMPLETED) {
                // task already completed
                throw new TaskNotCancelableError();
            }

            // cancel the task
            final TaskUpdater updater = new TaskUpdater(context, eventQueue);
            updater.cancel();
        }
    }
}
