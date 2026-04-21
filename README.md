# A2A-Demo

A small, Marvel-flavored demo of the [Agent-to-Agent (A2A) protocol 1.0](https://a2aproject.ai/).
Three agents cooperate to recover the Infinity Stones and "snap" the universe
back into shape:

```
        ┌──────────────┐      mission        ┌──────────────┐     object      ┌──────────────┐     stones     ┌──────────────┐
        │  Nick Wooly  │ ──────────────────► │   Iron-Ram   │ ──────────────► │ Bruce Baaner │ ─────────────► │    result    │
        │ (orchestrator│                     │   A2A :8080  │                 │  A2A :8081   │                │              │
        │ + LLM)       │                     │              │                 │              │                │              │
        └──────────────┘                     └──────────────┘                 └──────────────┘                └──────────────┘
```

1. **Nick Wooly** figures out *what* needs to be collected for the mission
   (he names the object).
2. **Iron-Ram** flies through the universe, lists matching objects, navigates
   to each and collects them. Returns a JSON list of stones.
3. **Bruce Baaner** receives the stones, verifies he got all 6, and snaps his
   fingers to restore the universe.

## Repository layout

| Path               | What it is                                                                 |
|--------------------|----------------------------------------------------------------------------|
| `iron-ram/`        | Quarkus / LangChain4j A2A server — the stone collector. Port **8080**.    |
| `bruce-baaner/`    | Quarkus / LangChain4j A2A server — the snapper. Port **8081**.            |
| `bruce_baaner.py`  | Python port of `bruce-baaner` using A2A SDK 1.0 (same behaviour, same port). |
| `nick-wooly/`      | Orchestrator. Plain Java `main` that wires the two A2A servers together.  |
| `pom.xml`          | Maven parent POM for all three Java modules.                               |
| `requirements.txt` | Python dependencies for `bruce_baaner.py`.                                 |

## Prerequisites

- **Java 21+** and **Maven 3.9+** (or use the included `./mvnw`).
- **Python 3.10+** and **[uv](https://docs.astral.sh/uv/)** (only if you run the Python port).
- **[Ollama](https://ollama.com/)** running locally on `http://localhost:11434`
  with the `gemma4` model pulled:
  ```bash
  ollama pull gemma4
  ```
  `nick-wooly` additionally uses `granite4:latest`:
  ```bash
  ollama pull granite4:latest
  ```

## Building the Java modules

From the repository root:

```bash
./mvnw clean install -DskipTests
```

## Running the agents

The two A2A servers must be running **before** you launch Nick Wooly.

### 1. Iron-Ram (port 8080)

```bash
cd iron-ram
../mvnw quarkus:dev
```

Its agent card is then available at:
<http://localhost:8080/.well-known/agent-card.json>

### 2. Bruce Baaner (port 8081)

Pick **one** of the two implementations.

**a) Java (Quarkus):**
```bash
cd bruce-baaner
../mvnw quarkus:dev
```

**b) Python:**

1. Create a virtual environment and install dependencies:
   ```bash
   uv venv .venv --python 3.12
   uv pip install -r requirements.txt
   ```

2. Pull the Ollama model (if not done already):
   ```bash
   ollama pull gemma4
   ```

3. Start the server:
   ```bash
   .venv/bin/python bruce_baaner.py
   ```

   Optional environment variables (all have defaults):
   ```bash
   OLLAMA_MODEL=gemma4 \
   OLLAMA_HOST=http://localhost:11434 \
   HOST=0.0.0.0 \
   PORT=8081 \
   PUBLIC_URL=http://localhost:8081 \
   .venv/bin/python bruce_baaner.py
   ```

Either way the agent card is at:
<http://localhost:8081/.well-known/agent-card.json>

> The Python port is a drop-in replacement for the Java server: same name,
> same port, same skill, same response strings. Use whichever you prefer.

### 3. Nick Wooly (orchestrator)

With both A2A servers running:

```bash
cd nick-wooly
../mvnw compile exec:java -Dexec.mainClass=be.lomagnette.a2a.wooly.Main
```

Nick Wooly will:
1. Ask its LLM what object the mission requires.
2. Hand that off to Iron-Ram over A2A → receives a list of stones.
3. Hand the stones to Bruce Baaner over A2A → receives the snap result.
4. Print the final mission result to stdout.

## Configuration

| Variable           | Applies to                | Default                       |
|--------------------|---------------------------|-------------------------------|
| `quarkus.http.port`| Java servers              | `8080` / `8081`               |
| `OLLAMA_MODEL`     | `bruce_baaner.py`         | `gemma4`                      |
| `OLLAMA_HOST`      | `bruce_baaner.py`         | `http://localhost:11434`      |
| `HOST`             | `bruce_baaner.py`         | `0.0.0.0`                     |
| `PORT`             | `bruce_baaner.py`         | `8081`                        |
| `PUBLIC_URL`       | `bruce_baaner.py`         | `http://localhost:$PORT`      |

The Java servers read `src/main/resources/application.properties`; adjust the
`quarkus.langchain4j.ollama.chat-model.model-id` there if you want a different
model.

## Talking to an agent directly

Either A2A server can be queried on its own without the orchestrator — any
A2A 1.0 client works. For a quick smoke test with `curl`:

```bash
curl -s http://localhost:8081/.well-known/agent-card.json | jq .
```
