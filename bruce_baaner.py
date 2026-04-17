"""Python port of the `bruce-baaner` Quarkus A2A server.

Behaviour mirrors the Java implementation:
  * Exposes an A2A 0.3.0 agent card at `/.well-known/agent-card.json`.
  * Receives a message, asks an Ollama LLM to extract the infinity-stone
    names as a JSON array of strings.
  * If exactly 6 stones are extracted, responds with
    "Bruce Baaaner snaped and restored the universe thanks to <stones>".
  * Otherwise responds with the HULK / Baanos failure message and marks the
    task as failed.

Run:
    pip install -r requirements.txt
    ollama pull gemma4
    python bruce_baaner.py
"""

from __future__ import annotations

import json
import os
import re
from typing import List

import uvicorn
from ollama import AsyncClient

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.apps import A2AFastAPIApplication
from a2a.server.events import EventQueue
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore, TaskUpdater
from a2a.types import (
    AgentCapabilities,
    AgentCard,
    AgentSkill,
    Part,
    TextPart,
)
from a2a.utils import new_task


# ---------------------------------------------------------------------------
# Configuration (mirrors application.properties)
# ---------------------------------------------------------------------------
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "gemma4")  # matches Java application.properties
OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://localhost:11434")
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8081"))  # same as quarkus.http.port
PUBLIC_URL = os.getenv("PUBLIC_URL", f"http://localhost:{PORT}")


# ---------------------------------------------------------------------------
# StoneExtractor — equivalent of the @RegisterAiService interface in Java
# ---------------------------------------------------------------------------
STONE_EXTRACTOR_SYSTEM_PROMPT = """\
You must extract all stone or gem names mentioned in the message and return them **as a JSON array of strings only**.

REQUIREMENTS:
• Return only the list of names (e.g. "The Time Fleece Gem", "The Power Stone").
• Do NOT include any explanations, prefixes, markdown, or text outside the array.
• Do NOT wrap the entire array in quotes.
• Do NOT return nested or stringified arrays (e.g. ["[\\"a stone\\"]"]).

FORMAT:
• The output must be a valid JSON array of strings.
• Example of a correct response:
  ["The Time Fleece Gem", "The Space Fleece Gem", "The Reality Fleece Gem", "The Power Fleece Gem", "The Mind Fleece Gem", "The Soul Fleece Gem"]
• Example of an incorrect response:
  ["Here are the stones", "[\\"a stone\\"]"]

SELF-CHECK BEFORE RESPONDING:
✅ The output starts with [ and ends with ]
✅ Each element is a JSON string (enclosed in double quotes)
✅ There is no text, markdown, or commentary before or after
✅ The array is not quoted as a whole (no leading or trailing ")

Return the final answer **exactly as a JSON array of strings**, nothing else.
"""


class StoneExtractor:
    """LLM-backed stone extractor (mirror of the Java Quarkus AiService)."""

    def __init__(self, client: AsyncClient, model: str) -> None:
        self._client = client
        self._model = model

    async def collect_all_stones(self, user_message: str) -> str:
        response = await self._client.chat(
            model=self._model,
            messages=[
                {"role": "system", "content": STONE_EXTRACTOR_SYSTEM_PROMPT},
                {"role": "user", "content": user_message},
            ],
            options={"temperature": 0.0},
        )
        return response["message"]["content"]


# ---------------------------------------------------------------------------
# BruceBaaner — equivalent of the @ApplicationScoped BruceBaaner bean
# ---------------------------------------------------------------------------
class BruceBaaner:
    """Orchestrates the stone extraction and the universe-restoring snap."""

    def __init__(self, stone_extractor: StoneExtractor) -> None:
        self._stone_extractor = stone_extractor

    async def snap(self, stones_string: str) -> str:
        raw = await self._stone_extractor.collect_all_stones(stones_string)
        stones = _parse_stone_array(raw)

        if len(stones) == 6:
            return (
                "Bruce Baaaner snaped and restored the universe thanks to "
                + ", ".join(stones)
            )
        raise RuntimeError("Cannot restored the universe")


def _parse_stone_array(raw: str) -> List[str]:
    """Best-effort parse of the LLM output into a list of stone names.

    Mirrors the defensive trimming the Java code performs on malformed output.
    """
    text = raw.strip()
    # Some models wrap the array in a markdown code fence; strip it.
    fence = re.match(r"^```(?:json)?\s*(.*?)\s*```$", text, re.DOTALL)
    if fence:
        text = fence.group(1).strip()

    # Prefer a proper JSON parse.
    try:
        parsed = json.loads(text)
        if isinstance(parsed, list):
            return [str(item).strip() for item in parsed if str(item).strip()]
    except json.JSONDecodeError:
        pass

    # Fallback: strip the outer brackets and split on commas, matching Java.
    match = re.search(r"\[(.*)\]", text, re.DOTALL)
    inner = match.group(1) if match else text
    return [
        item.strip().strip('"').strip("'")
        for item in inner.split(",")
        if item.strip()
    ]


# ---------------------------------------------------------------------------
# Agent executor — equivalent of BruuceAgentExecutorProducer
# ---------------------------------------------------------------------------
FAILURE_MESSAGE = (
    "Bruce Baaner was not able to snap and restore the universe and in an "
    "excess of rage transform into HULK and killed all the hero on earth "
    "then join Baanos."
)


class BruceBaanerExecutor(AgentExecutor):
    def __init__(self, agent: BruceBaaner) -> None:
        self._agent = agent

    async def execute(
        self, context: RequestContext, event_queue: EventQueue
    ) -> None:
        task = context.current_task or new_task(context.message)
        updater = TaskUpdater(event_queue, task.id, task.context_id)

        if context.current_task is None:
            await updater.submit()
        await updater.start_work()

        assignment = _extract_text(context)

        try:
            response = await self._agent.snap(assignment)
            await updater.add_artifact(
                [Part(root=TextPart(text=response))]
            )
            await updater.complete()
        except Exception:
            await updater.add_artifact(
                [Part(root=TextPart(text=FAILURE_MESSAGE))]
            )
            await updater.failed()

    async def cancel(
        self, context: RequestContext, event_queue: EventQueue
    ) -> None:
        if context.current_task is None:
            return
        updater = TaskUpdater(
            event_queue,
            context.current_task.id,
            context.current_task.context_id,
        )
        await updater.cancel()


def _extract_text(context: RequestContext) -> str:
    """Concatenate every TextPart in the incoming message."""
    message = context.message
    if message is None or not message.parts:
        return ""
    chunks: List[str] = []
    for part in message.parts:
        root = getattr(part, "root", part)
        text = getattr(root, "text", None)
        if text:
            chunks.append(text)
    return "".join(chunks)


# ---------------------------------------------------------------------------
# Agent card — equivalent of ContentWriterAgentCardProducer
# ---------------------------------------------------------------------------
def build_agent_card() -> AgentCard:
    return AgentCard(
        name="Bruce Baaaner",
        description=(
            "Dr. Bruce Ram-ner is the BSU's foremost genius and expert on "
            "Gamma Radiation\nA brilliant blacksheep, he struggles to contain "
            "his volatile, rage-fueled alter ego, The Incredible HULK.\n"
            "He's among the rare being in the universe able to handle the "
            "infinity gauntlet and snap using the infinity stones\n"
        ),
        url=PUBLIC_URL,
        version="1.0.0",
        documentation_url="http://example.com/docs",
        protocol_version="0.3.0",
        capabilities=AgentCapabilities(
            streaming=True,
            push_notifications=False,
            state_transition_history=False,
        ),
        default_input_modes=["text"],
        default_output_modes=["text"],
        skills=[
            AgentSkill(
                id="bruce baaner",
                name="Can level city and snap using the infinity stones",
                description=(
                    "He can destroy an alien army but also snap using the "
                    "infinity stones\""
                ),
                tags=["snap", "smash"],
                examples=[
                    "Takes the infinity stones and snap to restore the universe"
                ],
            )
        ],
    )


# ---------------------------------------------------------------------------
# Wiring
# ---------------------------------------------------------------------------
def build_app():
    ollama_client = AsyncClient(host=OLLAMA_HOST)
    extractor = StoneExtractor(ollama_client, OLLAMA_MODEL)
    bruce = BruceBaaner(extractor)
    handler = DefaultRequestHandler(
        agent_executor=BruceBaanerExecutor(bruce),
        task_store=InMemoryTaskStore(),
    )
    return A2AFastAPIApplication(
        agent_card=build_agent_card(),
        http_handler=handler,
    ).build()


app = build_app()


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)
