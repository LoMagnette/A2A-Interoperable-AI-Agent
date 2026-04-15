package be.lomagnette.a2a.baaner;

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
public final class ContentWriterAgentCardProducer {

  /** HTTP port for the agent. */
  private int httpPort;

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
    return new AgentCard.Builder()
        .name("Bruce Baaaner")
        .description("""
            Dr. Bruce Ram-ner is the BSU's foremost genius and expert on Gamma Radiation
            A brilliant blacksheep, he struggles to contain his volatile, rage-fueled alter ego, The Incredible HULK.
            He's among the rare being in the universe able to handle the infinity gauntlet and snap using the infinity stones
            """
        )
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
                    .id("bruce baaner")
                    .name("Can level city and snap using the infinity stones")
                    .description("""
                              He can destroy an alien army but also snap using the infinity stones"
                              """)
                    .tags(List.of("snap","smash"))
                    .examples(
                        List.of(
                            "Takes the infinity stones and snap to restore the universe"))
                    .build()))
        .protocolVersion("0.3.0")
        .build();
  }
}
