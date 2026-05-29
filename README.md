# VibeMine

**Vibe coding, inside Minecraft.**

VibeMine is a Paper plugin that lets players build and hot-swap small Minecraft plugins by chatting with an AI agent in-game. Start a vibe session, describe the command or behavior you want, and VibeMine turns it into a sandboxed JavaScript “VibePlugin” powered by GraalJS.

## ✨ What it does

- 🧠 **In-game AI coding sessions** with conversation history
- ⚡ **Hot-swappable generated plugins** — enable, disable, update, or delete without rebuilding the server plugin
- 🧩 **JavaScript plugin runtime** using safe wrapper APIs instead of raw Bukkit objects
- 🎮 **Command and event generation** for common Minecraft interactions
- 🗃️ **Persistent storage** for sessions and generated plugin data via SQLite
- 🛠️ **Built-in tools** for API lookup, command execution, ray tracing, spawning, messaging, and syntax highlighting
- 🔐 **Permission-gated management** commands for server operators

## 🚀 Quick start

### Requirements

- Java 25
- Gradle wrapper included in this repository
- A Paper server compatible with the configured API version
- An OpenAI API key

### Build

```bash
./gradlew build
```

The shaded plugin jar is produced under:

```text
build/libs/
```

### Run a local Paper server

```bash
./gradlew runServer
```

The development server is configured in `build.gradle.kts` via the `run-paper` Gradle plugin.

## ⚙️ Configuration

On first launch, VibeMine creates its config from `src/main/resources/config.yml`:

```yaml
database:
  file: "vibe.db"
ai:
  api_key: "<your api key>"
  model: "gpt-5.4-mini"
```

Set your API key in the generated plugin config folder before using AI features.

## 💬 Commands

| Command | Description |
| --- | --- |
| `/vibe` or `/vibe start` | Start a new vibe session |
| `/vibe continue [session-id]` | Resume your latest or selected saved session |
| `/vibe sessions` | List recent saved sessions |
| `/vibe stop` | Save and stop your active session |
| `/vibe plugins` | List generated VibePlugins |
| `/vibe enable <plugin>` | Enable a generated plugin |
| `/vibe disable <plugin>` | Disable a generated plugin |
| `/vibe delete <plugin>` | Delete a generated plugin |

## 🔐 Permissions

| Permission | Default |
| --- | --- |
| `vibemine.vibe.start` | Everyone |
| `vibemine.vibe.continue` | Everyone |
| `vibemine.vibe.sessions` | Everyone |
| `vibemine.vibe.stop` | Everyone |
| `vibemine.vibe.plugins` | Operators |
| `vibemine.vibe.plugins.enable` | Operators |
| `vibemine.vibe.plugins.disable` | Operators |
| `vibemine.vibe.plugins.delete` | Operators |

## 🧪 Example vibes

Try prompts like:

- “Make a `/fly` command that toggles flight for the player.”
- “Create a welcome message when players join.”
- “Give me a magic stick that shoots lightning when I right-click.”
- “Add an undo command to the last block-changing plugin.”
- “Make chat messages gold if the player is holding an emerald.”

VibeMine can generate command handlers, event listeners, persistent data, formatted messages, custom items, sounds, world interactions, and more.

## 🧱 How it works

1. A player starts a conversation with `/vibe start`.
2. Chat messages are sent to the AI agent.
3. The agent returns structured JSON describing a VibePlugin or patch.
4. VibeMine validates and loads the generated JavaScript with GraalJS.
5. The plugin can be managed with `/vibe plugins`, `/vibe enable`, `/vibe disable`, and `/vibe delete`.

Generated plugins use VibeMine runtime wrappers such as players, worlds, blocks, entities, inventories, scheduler, database, and MiniMessage integration.

## 🛡️ Safety model

VibePlugins are designed to use a constrained runtime API rather than direct Paper internals. The AI is instructed to avoid filesystem access, network access, process execution, reflection, class loading, raw Polyglot APIs, and dangerous permission changes.

As with any code-generating tool, review generated behavior before giving broad access on public servers.

## 🛠️ Development

Useful commands:

```bash
./gradlew build       # compile and create the shaded jar
./gradlew runServer   # launch a local Paper test server
```

Project highlights:

```text
src/main/java/io/rcw/vibemine/
├── ai/               # agent, tools, conversation storage, generated plugin runtime
├── commands/         # /vibe command
├── handlers/         # chat and AI response listeners
├── code/             # syntax highlighting support
└── serialization/    # Gson adapters for Bukkit/Paper types
```

## 📄 License

No license has been specified yet.
