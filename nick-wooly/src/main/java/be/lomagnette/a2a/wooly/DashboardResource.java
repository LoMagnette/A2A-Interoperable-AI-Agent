package be.lomagnette.a2a.wooly;

import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * Mission Control endpoints. The page itself is the static
 * {@code META-INF/resources/index.html}, served by Quarkus at {@code /}.
 */
@Path("/")
public class DashboardResource {

    @Inject
    EventBus bus;

    @Inject
    MissionService mission;

    /** Live event feed consumed by the dashboard's EventSource. */
    @GET
    @Path("events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> events() {
        return bus.stream();
    }

    /** Ingest a single JSON event from any agent or tool. */
    @POST
    @Path("event")
    @Consumes(MediaType.APPLICATION_JSON)
    public void ingest(String event) {
        if (event != null && !event.isBlank()) {
            bus.publish(event.trim());
        }
    }

    /** Launch the mission in the background (triggered by the dashboard button). */
    @POST
    @Path("launch")
    public Response launch() {
        return mission.launch()
                ? Response.accepted().build()
                : Response.status(Response.Status.CONFLICT).build();
    }

    /** Clear the current run. */
    @POST
    @Path("reset")
    public void reset() {
        bus.reset();
    }
}
