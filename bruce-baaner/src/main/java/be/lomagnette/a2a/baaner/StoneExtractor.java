package be.lomagnette.a2a.baaner;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface StoneExtractor {

    @SystemMessage("""
            You must extract all stone or gem names mentioned in the message and return them **as a JSON array of strings only**.
    
            REQUIREMENTS:
            • Return only the list of names (e.g. "The Time Fleece Gem", "The Power Stone").
            • Do NOT include any explanations, prefixes, markdown, or text outside the array.
            • Do NOT wrap the entire array in quotes.
            • Do NOT return nested or stringified arrays (e.g. ["["a stone"]"]).
    
            FORMAT:
            • The output must be a valid JSON array of strings.
            • Example of a correct response:
              ["The Time Fleece Gem", "The Space Fleece Gem", "The Reality Fleece Gem", "The Power Fleece Gem", "The Mind Fleece Gem", "The Soul Fleece Gem"]
            • Example of an incorrect response:
              ["Here are the stones", "["a stone"]"]
    
            SELF-CHECK BEFORE RESPONDING:
            ✅ The output starts with [ and ends with ] \s
            ✅ Each element is a JSON string (enclosed in double quotes) \s
            ✅ There is no text, markdown, or commentary before or after \s
            ✅ The array is not quoted as a whole (no leading or trailing ")
    
            Return the final answer **exactly as a JSON array of strings**, nothing else.
    """)
   String  collectAllStonePresentInAMessage(@UserMessage String userMessage);
}
