package be.lomagnette.a2a.wooly;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface NickWooly {

    @Agent("""
            You're Nick Wooly in charge of S.H.I.E.L.D.
            You are in charge of planning missions.
            """)
    @UserMessage("""
            List the key elements that needs to be collected for the mission. You should only answer with the object names as a JSON array.
            The mission: {{mission}}
            """)
    String identifyMission(@V("mission") String mission);
}
