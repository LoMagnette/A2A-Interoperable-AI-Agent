# A2A-Demo

A small, Marvel-flavored demo of the [Agent-to-Agent (A2A) protocol 1.0](https://a2aproject.ai/)
combined with an [MCP](https://modelcontextprotocol.io/) tool server. Three
agents cooperate to recover the Infinity Stones and "snap" the universe back
into shape:

```
 ┌──────────────┐  mission   ┌──────────────┐   object    ┌──────────────┐   stones    ┌──────────────┐
 │  Nick Wooly  │ ─────────► │   Iron-Ram   │ ──────────► │ Bruce Baaner │ ──────────► │    result    │
 │ (orchestrator│            │   A2A :8080  │             │  A2A :8081   │             │              │
 │  + LLM)      │            └──────┬───────┘             └──────────────┘             └──────────────┘
 └──────────────┘                   │ MCP (SSE)
                                    ▼
                            ┌──────────────────┐
                            │ Iron-Ram Garage  │
                            │  MCP :8082       │
                            │ (Baarvis +       │
                            │  IronRamArmor)   │
                            └──────────────────┘
```

1. **Nick Wooly** figures out *what* needs to be collected for the mission
   (he names the object).
2. **Iron-Ram** receives the description and orchestrates the search/collect
   loop. It does not hold the tools itself anymore — it calls them over MCP
   on the **Iron-Ram Garage** server. Returns a JSON list of stones.
3. **Iron-Ram Garage** is an MCP server that exposes two tools backed by a
   Postgres `KeyObject` table:
   - `Baarvis` — list candidate objects matching a set of keywords.
   - `collect` (IronRamArmor) — navigate to a location and collect a named object.
4. **Bruce Baaner** receives the stones, verifies he got all 6, and snaps his
   fingers to restore the universe.

## Repository layout

| Path                 | What it is                                                                 |
|----------------------|----------------------------------------------------------------------------|
| `iron-ram/`          | Quarkus / LangChain4j A2A server — orchestrates the search/collect loop, calls MCP tools. Port **8080**. |
| `iron-ram-garage/`   | Quarkus MCP server (SSE) — exposes the `Baarvis` and `collect` tools, owns the `KeyObject` Postgres table + `import.sql`. Port **8082**. |
| `bruce-baaner/`      | Quarkus / LangChain4j A2A server — the snapper. Port **8081**.            |
| `bruce_baaner.py`    | Python port of `bruce-baaner` using A2A SDK 1.0 (same behaviour, same port). |
| `nick-wooly/`        | Orchestrator. Plain Java `main` that wires the two A2A servers together.  |
| `pom.xml`            | Maven parent POM for all four Java modules.                                |
| `requirements.txt`   | Python dependencies for `bruce_baaner.py`.                                 |

## Prerequisites

- **Java 25** and **Maven 3.9+** (or use the included `./mvnw`).
  `nick-wooly` is built with `maven.compiler.release=25`.
- **Docker** (or Podman) — the Quarkus modules use Dev Services to spin up a
  Postgres container automatically. Required for `iron-ram` and `iron-ram-garage`.
- **Python 3.10+** and **[uv](https://docs.astral.sh/uv/)** (only if you run the Python port).
- **[Ollama](https://ollama.com/)** running locally on `http://localhost:11434`
  with the `gemma4` model pulled:
  ```bash
  ollama pull gemma4
  ```

## Building the Java modules

From the repository root:

```bash
./mvnw clean install -DskipTests
```

## Running the agents

The MCP server and the two A2A servers must all be running **before** you
launch Nick Wooly. Start them in this order so each downstream dependency is
available when the next one boots.

### 1. Iron-Ram Garage (MCP, port 8082)

```bash
cd iron-ram-garage
../mvnw quarkus:dev
```

This boots a Postgres Dev Service, loads `import.sql` with the `KeyObject`
catalogue (Infinity Stones and other Marvel-flavored items), and exposes the
MCP endpoint at <http://localhost:8082/mcp>.

### 2. Iron-Ram (A2A, port 8080)

```bash
cd iron-ram
../mvnw quarkus:dev
```

`iron-ram` connects to the garage's MCP endpoint (configured in
`application.properties` as `quarkus.langchain4j.mcp.garage.url`). Its agent
card is then available at:
<http://localhost:8080/.well-known/agent-card.json>

### 3. Bruce Baaner (port 8081)

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

### 4. Nick Wooly (orchestrator)

With the MCP server and both A2A servers running:

```bash
cd nick-wooly
../mvnw compile exec:java -Dexec.mainClass=be.lomagnette.a2a.wooly.Main
```

Nick Wooly will:
1. Ask its LLM what object the mission requires.
2. Hand that off to Iron-Ram over A2A → Iron-Ram calls the garage's MCP tools
   to find and collect each object, then returns the JSON list of stones.
3. Hand the stones to Bruce Baaner over A2A → receives the snap result.
4. Print the final mission result to stdout.

> `nick-wooly` currently depends on `langchain4j-agentic-a2a:1.16.0-beta26-SNAPSHOT`.
> On may 2026, you need to build your own version of this module that use the latest version A2A client (in your `~/.m2/settings.xml`
> or `nick-wooly/pom.xml`) for Maven to resolve it.

## Configuration

| Variable           | Applies to                | Default                       |
|--------------------|---------------------------|-------------------------------|
| `quarkus.http.port`| Java servers              | `8080` / `8081` / `8082`      |
| `OLLAMA_MODEL`     | `bruce_baaner.py`         | `gemma4`                      |
| `OLLAMA_HOST`      | `bruce_baaner.py`         | `http://localhost:11434`      |
| `HOST`             | `bruce_baaner.py`         | `0.0.0.0`                     |
| `PORT`             | `bruce_baaner.py`         | `8081`                        |
| `PUBLIC_URL`       | `bruce_baaner.py`         | `http://localhost:$PORT`      |

The Java servers read `src/main/resources/application.properties`. Notable
settings:

- `iron-ram` — `quarkus.langchain4j.ollama.chat-model.model-id=gemma4`,
  `quarkus.langchain4j.mcp.garage.url=http://localhost:8082/mcp` with
  `transport-type=streamable-http`. There is
  also a `quarkus.langchain4j.timeout=60s` workaround for
  [quarkiverse/quarkus-langchain4j#2340](https://github.com/quarkiverse/quarkus-langchain4j/issues/2340)
  on Quarkus 3.35.x.
- `iron-ram-garage` — `quarkus.mcp.server.server-info.name=iron-ram-garage`,
  CORS enabled.

## Talking to an agent directly

Either A2A server can be queried on its own without the orchestrator — any
A2A 1.0 client works. For a quick smoke test with `curl`:

```bash
curl -s http://localhost:8080/.well-known/agent-card.json | jq .
curl -s http://localhost:8081/.well-known/agent-card.json | jq .
```

And the MCP server can be probed at its streamable HTTP endpoint:

```bash
curl -N -H "Accept: application/json, text/event-stream" \
  -H "Content-Type: application/json" \
  -X POST http://localhost:8082/mcp \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```
