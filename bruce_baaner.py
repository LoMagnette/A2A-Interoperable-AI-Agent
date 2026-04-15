"""BruceBaaner — an A2A 0.3.0 agent powered by Ollama (gemma3 by default).

His job: confirm he received all 6 Infinity Stones and snapped everybody back.

Run:
    pip install "a2a-sdk>=0.3.0" ollama uvicorn fastapi
    ollama pull gemma3
    python bruce_baaner.py

Then query it via any A2A 0.3.0 client at http://localhost:8000
The Agent Card is served at http://localhost:8000/.well-known/agent-card.json
"""

from __future__ import annotations

import os
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
    AgentProvider,
    AgentSkill,
    TransportProtocol,
)
from a2a.utils import new_agent_text_message, new_task


OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "gemma3")
OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://localhost:11434")
HOST = os.getenv("HOST", "0.0.0.0")
PORT = int(os.getenv("PORT", "8000"))
PUBLIC_URL = os.getenv("PUBLIC_URL", f"http://localhost:{PORT}/")

INFINITY_STONES = ["Space", "Mind", "Reality", "Power", "Time", "Soul"]

SYSTEM_PROMPT = f"""You are BruceBaaner, the Hulk wearing the Infinity Gauntlet.
You have successfully gathered all six Infinity Stones: {", ".join(INFINITY_STONES)}.
You have just snapped your fingers and brought everyone who was dusted back to life.
Your arm is scorched and aching, but you are triumphant, humble, and a little shaken.

When asked, confirm:
  1. You received all 6 Infinity Stones.
  2. The snap worked — everybody is back.
  3. Acknowledge the cost (your arm).

Speak in character as Bruce Banner / Hulk: thoughtful, scientific, slightly weary,
but warm. Keep answers concise unless pressed for detail.
"""


class BruceBaanerExecutor(AgentExecutor):
    def __init__(self) -> None:
        self._ollama = AsyncClient(host=OLLAMA_HOST)

    async def execute(
        self, context: RequestContext, event_queue: EventQueue
    ) -> None:
        message = context.message
        task = context.current_task or new_task(message)
        updater = TaskUpdater(event_queue, task.id, task.context_id)

        if context.current_task is None:
            await updater.submit()
        await updater.start_work()

        user_text = ""
        if message and message.parts:
            for p in message.parts:
                root = getattr(p, "root", p)
                if getattr(root, "text", None):
                    user_text += root.text

        if not user_text.strip():
            user_text = "Did you get all six stones? Did the snap work?"

        try:
            response = await self._ollama.chat(
                model=OLLAMA_MODEL,
                messages=[
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": user_text},
                ],
            )
            reply = response["message"]["content"]
        except Exception as e:  # surface ollama errors to the caller
            await updater.failed(
                message=new_agent_text_message(
                    f"BruceBaaner couldn't reach Ollama ({OLLAMA_MODEL}): {e}",
                    task.context_id,
                    task.id,
                )
            )
            return

        await updater.complete(
            message=new_agent_text_message(reply, task.context_id, task.id)
        )

    async def cancel(
        self, context: RequestContext, event_queue: EventQueue
    ) -> None:
        if context.current_task:
            updater = TaskUpdater(
                event_queue,
                context.current_task.id,
                context.current_task.context_id,
            )
            await updater.cancel()


def build_agent_card() -> AgentCard:
    return AgentCard(
        name="BruceBaaner",
        description=(
            "The Hulk after the snap. Confirms receipt of all 6 Infinity Stones "
            "and reports that everybody has been snapped back."
        ),
        url=PUBLIC_URL,
        version="1.0.0",
        protocol_version="0.3.0",
        provider=AgentProvider(
            organization="Avengers R&D",
            url="https://example.com/avengers",
        ),
        default_input_modes=["text/plain"],
        default_output_modes=["text/plain"],
        preferred_transport=TransportProtocol.jsonrpc,
        capabilities=AgentCapabilities(
            streaming=False,
            push_notifications=False,
            state_transition_history=False,
        ),
        skills=[
            AgentSkill(
                id="confirm-stones",
                name="Confirm Infinity Stones",
                description=(
                    "Reports whether all six Infinity Stones "
                    "(Space, Mind, Reality, Power, Time, Soul) were gathered."
                ),
                tags=["infinity-stones", "avengers", "status"],
                examples=[
                    "Did you get all six stones?",
                    "Which stones do you have?",
                ],
            ),
            AgentSkill(
                id="report-snap",
                name="Report the Snap",
                description="Confirms the snap succeeded and everyone is back.",
                tags=["snap", "avengers"],
                examples=[
                    "Did the snap work?",
                    "Is everybody back?",
                ],
            ),
        ],
    )


def build_app():
    handler = DefaultRequestHandler(
        agent_executor=BruceBaanerExecutor(),
        task_store=InMemoryTaskStore(),
    )
    return A2AFastAPIApplication(
        agent_card=build_agent_card(),
        http_handler=handler,
    ).build()


app = build_app()


if __name__ == "__main__":
    uvicorn.run(app, host=HOST, port=PORT)
