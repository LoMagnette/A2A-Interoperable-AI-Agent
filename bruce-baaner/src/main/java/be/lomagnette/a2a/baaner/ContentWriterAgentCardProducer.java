package be.lomagnette.a2a.baaner;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.List;

/**
 * Producer for Content Writer Agent Card.
 */
@ApplicationScoped
public final class ContentWriterAgentCardProducer {

    /**
     * HTTP port for the agent.
     */
    private final int httpPort;

    public ContentWriterAgentCardProducer(@ConfigProperty(name = "quarkus.http.port") int httpPort) {
        this.httpPort = httpPort;
    }


    /**
     * Creates the agent card for the content writer agent.
     *
     * @return the agent card
     */
    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name("Bruce Baaaner")
                .description("""
                        Dr. Bruce Ram-ner is the BSU's foremost genius and expert on Gamma Radiation
                        A brilliant blacksheep, he struggles to contain his volatile, rage-fueled alter ego, The Incredible HULK.
                        He's among the rare being in the universe able to handle the infinity gauntlet and snap using the infinity stones
                        """
                )
                .supportedInterfaces(
                        List.of(
                                new AgentInterface("JSONRPC", "http://localhost:" + httpPort)
                        )
                )
                .version("1.0.0")
                .documentationUrl("http://example.com/docs")
                .capabilities(
                        AgentCapabilities.builder()
                                .streaming(true)
                                .pushNotifications(false)
                                .build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(
                        Collections.singletonList(
                                AgentSkill.builder()
                                        .id("bruce baaner")
                                        .name("Can level city and snap using the infinity stones")
                                        .description("""
                                                He can destroy an alien army but also snap using the infinity stones"
                                                """)
                                        .tags(List.of("snap", "smash"))
                                        .examples(
                                                List.of(
                                                        "Takes the infinity stones and snap to restore the universe"))
                                        .build()))
                .build();
    }
}
