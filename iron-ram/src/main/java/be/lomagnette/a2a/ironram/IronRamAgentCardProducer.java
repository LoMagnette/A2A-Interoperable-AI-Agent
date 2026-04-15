package be.lomagnette.a2a.ironram;

import io.a2a.server.PublicAgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.List;

/** Producer for Content Writer Agent Card. */
@ApplicationScoped
public final class IronRamAgentCardProducer {

  /** HTTP port for the agent. */
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
    return new AgentCard.Builder()
        .name("IronRam Agent")
        .description("""
                      IronRam, genius, billionaire, philantropist.
                      He's a super hero to protect universe 8444.
                      He can fly through space using your IronRamArmor navigation capability.
                      He can also collect object through the universe.
            """)
        .url("http://localhost:" + httpPort)
        .version("1.0.0")
        .documentationUrl("http://example.com/docs")
        .capabilities(
            new AgentCapabilities.Builder()
                .streaming(true)
                .pushNotifications(false)
                .stateTransitionHistory(false)
                .build())
        .defaultInputModes(Collections.singletonList("text"))
        .defaultOutputModes(Collections.singletonList("text"))
        .skills(
            Collections.singletonList(
                new AgentSkill.Builder()
                    .id("ironram")
                    .name("Goes through space and collect objects")
                    .description(
                            """
                            Goes through space using his IronRamArmor and collect objects based on a given description.
                            """)
                    .tags(List.of("collecter","super hero"))
                    .examples(
                        List.of(
                            "Go collect all the infinity stone"))
                    .build()))
        .protocolVersion("0.3.0")
        .build();
  }
}
