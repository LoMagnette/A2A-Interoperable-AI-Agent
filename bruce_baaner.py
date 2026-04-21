"""Python port of the ``bruce-baaner`` Quarkus A2A server.

Behaviour mirrors the Java implementation:
  * Exposes an A2A 1.0 agent card at ``/.well-known/agent-card.json``.
  * Receives a message, asks an Ollama LLM to extract the infinity-stone
    names as a JSON array of strings.
  * If exactly 6 stones are extracted, responds with
    "Bruce Baaaner snaped and restored the universe thanks to <stones>".
  * Otherwise responds with the HULK / Baanos failure message and marks the
    task as failed.

Run with uv::

    uv sync
    uv run python bruce_baaner.py
"""

from __future__ import annotations

import json
import os
import re

import uvicorn
from fastapi import FastAPI
from ollama import AsyncClient

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.routes import create_agent_card_routes, create_jsonrpc_routes
from a2a.server.routes.common import DefaultServerCallContextBuilder
from a2a.server.tasks import InMemoryTaskStore, TaskUpdater
from a2a.types import (
    AgentCapabilities,
    AgentCard,
    AgentInterface,
    AgentSkill,
    Part,
    Task,
    TaskState,
    TaskStatus,
)

# ---------------------------------------------------------------------------
# Configuration (mirrors application.properties)
# ---------------------------------------------------------------------------
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "gemma4")
OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://localhost:11434")
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8081"))
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


def _parse_stone_array(raw: str) -> list[str]:
    """Best-effort parse of the LLM output into a list of stone names.

    Mirrors the defensive trimming the Java code performs on malformed output.
    """
    text = raw.strip()
    # Some models wrap the array in a markdown code fence — strip it.
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

    # Fallback: strip outer brackets and split on commas (matching Java logic).
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
        # The v2 handler (ActiveTask) requires a Task object to be enqueued
        # before any TaskStatusUpdateEvent, so we submit one first.
        await event_queue.enqueue_event(
            Task(
                id=context.task_id,
                context_id=context.context_id,
                status=TaskStatus(state=TaskState.TASK_STATE_SUBMITTED),
                history=[context.message] if context.message else [],
            )
        )

        updater = TaskUpdater(
            event_queue=event_queue,
            task_id=context.task_id,
            context_id=context.context_id,
        )
        await updater.start_work()

        assignment = context.get_user_input() or ""

        try:
            response = await self._agent.snap(assignment)
            await updater.add_artifact(parts=[Part(text=response)])
            await updater.complete()
        except Exception:
            await updater.add_artifact(parts=[Part(text=FAILURE_MESSAGE)])
            await updater.failed()

    async def cancel(
        self, context: RequestContext, event_queue: EventQueue
    ) -> None:
        updater = TaskUpdater(
            event_queue=event_queue,
            task_id=context.task_id,
            context_id=context.context_id,
        )
        await updater.cancel()


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
        supported_interfaces=[
            AgentInterface(
                protocol_binding="JSONRPC",
                url=PUBLIC_URL,
                protocol_version="1.0"
            ),
        ],
        version="1.0.0",
        documentation_url="http://example.com/docs",
        capabilities=AgentCapabilities(
            streaming=True,
            push_notifications=False,
            extended_agent_card=False,
        ),
        default_input_modes=["text"],
        default_output_modes=["text"],
        skills=[
            AgentSkill(
                id="bruce baaner",
                name="Can level city and snap using the infinity stones",
                description=(
                    "He can destroy an alien army but also snap using the "
                    "infinity stones"
                ),
                tags=["snap", "smash"],
                examples=[
                    "Takes the infinity stones and snap to restore the universe"
                ],
            )
        ],
    )


# ---------------------------------------------------------------------------
# Version compat — the Java A2A SDK (1.0.0.Beta1) does not send the
# A2A-Version HTTP header. The Python SDK defaults a missing header to "0.3"
# and then rejects the request. This builder defaults to "1.0" instead.
# ---------------------------------------------------------------------------
class VersionDefaultingContextBuilder(DefaultServerCallContextBuilder):
    def build(self, request):
        context = super().build(request)
        headers = context.state.get("headers", {})
        if not headers.get("a2a-version"):
            headers["a2a-version"] = "1.0"
            context.state["headers"] = headers
        return context


# ---------------------------------------------------------------------------
# Wiring
# ---------------------------------------------------------------------------
def build_app() -> FastAPI:
    ollama_client = AsyncClient(host=OLLAMA_HOST)
    extractor = StoneExtractor(ollama_client, OLLAMA_MODEL)
    bruce = BruceBaaner(extractor)

    agent_card = build_agent_card()
    handler = DefaultRequestHandler(
        agent_executor=BruceBaanerExecutor(bruce),
        task_store=InMemoryTaskStore(),
        agent_card=agent_card,
    )

    app = FastAPI()
    app.routes.extend(create_agent_card_routes(agent_card=agent_card))
    app.routes.extend(
        create_jsonrpc_routes(
            request_handler=handler,
            rpc_url="/",
            context_builder=VersionDefaultingContextBuilder(),
            enable_v0_3_compat=False,
        )
    )
    return app


app = build_app()

if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)
