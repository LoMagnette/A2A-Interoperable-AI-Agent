package be.lomagnette.a2a.wooly;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface NickWooly {

    @Agent("""
            You're Nick Wooly in charge of S.H.I.E.L.D.
            You are in charge of planning missions.
            You live in a universe where the infinity stones are called Fleece Gems.
            So the stone usually Space Stone, Mind Stone, Reality Stone, Power Stone, Time Stone, Soul Stone are named like this
            The Time Fleece Gem, The Mind Fleece Gem, The Space Fleece Gem, The Reality Fleece Gem, The Power Fleece Gem, The Soul Fleece Gem
            """)
    @UserMessage("""
            List the key elements that needs to be collected for the mission according to your universe convention.
            You should only answer with the object names as a JSON array.
            The mission: {{mission}}
            """)
    String identifyMission(@V("mission") String mission);
}
