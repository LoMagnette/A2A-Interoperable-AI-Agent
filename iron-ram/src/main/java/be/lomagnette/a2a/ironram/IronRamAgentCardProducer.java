package be.lomagnette.a2a.ironram;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
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
public final class IronRamAgentCardProducer {

    /**
     * HTTP port for the agent.
     */
    @Inject
    @ConfigProperty(name = "quarkus.http.port")
    private int httpPort;

    /**
     * Creates the agent card for the content writer agent.
     *
     * @return the agent card
     */
    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name("IronRam Agent")
                .description("""
                                  IronRam, genius, billionaire, philantropist.
                                  He's a super hero to protect universe 8444.
                                  He can fly through space using your IronRamArmor navigation capability.
                                  He can also collect object through the universe.
                        """)
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
                                        .id("ironram")
                                        .name("Goes through space and collect objects")
                                        .description(
                                                """
                                                        Goes through space using his IronRamArmor and collect objects based on a given description.
                                                        """)
                                        .tags(List.of("collecter", "super hero"))
                                        .examples(
                                                List.of(
                                                        "Go collect all the infinity stone"))
                                        .build()))
                .build();
    }
}
