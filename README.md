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
- An eligible server-owner ChatGPT connection, or an OpenAI API key

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
  subscription:
    enabled: true
    model: "gpt-5.4-mini" # Select an eligible account model with `vibe auth models`.
    api_fallback: true # WARNING: API fallback incurs normal API charges.
```

Connect the server owner's ChatGPT account using the [local sign-in helper and secure import](docs/chatgpt-sign-in.md), or configure an API key. One connection serves the whole server; players do not sign in. API fallback is retained and configurable. Sign-in requires Java 25 and POSIX credential storage on the local computer and server; account eligibility and subscription limits still apply.

Manage the connection from the **local server console** with `vibe auth login`, `import`, `status`, `models`, and `logout`. Never paste credentials into Minecraft chat. Restart after config changes.

## 💬 Commands

| Command | Description |
| --- | --- |
| `/vibe` or `/vibe start` | Start a new vibe session |
| `/vibe continue [session-id]` | Resume your latest or selected saved session |
| `/vibe sessions` | List recent saved sessions |
| `/vibe stop` | Save and stop your active session |
| `/vibe plugins` | List generated VibePlugins |
| `/vibe ide` | Edit `.js` source in a multiline dialog titled with its filename (16,000-character limit). Save creates a workspace checkpoint; activate it separately. |
| `/vibe enable <plugin>` | Enable a generated plugin |
| `/vibe disable <plugin>` | Disable a generated plugin |
| `/vibe delete <plugin>` | Delete a generated plugin (checkpoint history is retained) |
| `/vibe state <plugin>` | Show workspace state and branches |
| `/vibe checkpoint <plugin> <message>` | Save all workspace edits |
| `/vibe branch <plugin> <branch> [checkpoint]` | Fork your workspace or create a branch at a checkpoint |
| `/vibe checkout <plugin> <branch>` | Switch a clean workspace |
| `/vibe revisions <plugin>` | List saved checkpoints |
| `/vibe activate <plugin> <checkpoint>` | Activate a checkpoint without discarding edits or later history |

## 🔐 Permissions

| Permission | Default |
| --- | --- |
| `vibemine.vibe.start` | Everyone |
| `vibemine.vibe.continue` | Everyone |
| `vibemine.vibe.sessions` | Everyone |
| `vibemine.vibe.stop` | Everyone |
| `vibemine.vibe.plugins` | Operators |
| `vibemine.vibe.ide` | Operators |
| `vibemine.vibe.plugins.enable` | Operators |
| `vibemine.vibe.plugins.disable` | Operators |
| `vibemine.vibe.plugins.delete` | Operators |
| `vibemine.vibe.plugins.revisions` | Operators |
| `vibemine.vibe.plugins.revert` | Operators |

Plugin source now lives in isolated persistent workspaces with SQLite checkpoints and branches. File writes do not auto-reload plugins. `/vibe enable` reloads the active version, not unpublished workspace edits. See [source state and recovery](docs/plugin-state.md) for agent tools, permissions, limits and backup instructions.

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
