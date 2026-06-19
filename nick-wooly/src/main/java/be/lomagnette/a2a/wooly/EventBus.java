package be.lomagnette.a2a.wooly;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory hub for mission events. Keeps a replay buffer for the current run
 * and broadcasts every event to all connected dashboards.
 */
@ApplicationScoped
public class EventBus {

    private static final int MAX_BUFFER = 5000;

    private final BroadcastProcessor<String> processor = BroadcastProcessor.create();
    private final List<String> buffer = new CopyOnWriteArrayList<>();

    /** Record an event and fan it out to every live subscriber. */
    public void publish(String event) {
        if (event.contains("\"type\":\"mission-start\"")) {
            buffer.clear();              // a new mission starts a fresh run
        }
        buffer.add(event);
        while (buffer.size() > MAX_BUFFER) {
            buffer.removeFirst();
        }
        processor.onNext(event);
    }

    /** Clear the current run. */
    public void reset() {
        buffer.clear();
        processor.onNext("{\"type\":\"reset\"}");
    }

    /** The buffered current run, followed by the live stream — so late joiners stay consistent. */
    public Multi<String> stream() {
        return Multi.createBy().concatenating().streams(
                Multi.createFrom().iterable(List.copyOf(buffer)),
                processor);
    }
}
