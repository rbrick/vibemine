# Plugin source state

VibeMine stores plugin source in `plugin-state.db` (SQLite). Git, JGit, signing keys and external executables are not required. Existing `vibed-plugins/<name>` source is imported on first workspace access. Existing `.git` directories are neither read nor changed; old Git history is not migrated.

## Four separate states

- **Workspace:** mutable source owned by a player UUID, shared by that player's agent and dialog editor. It survives restarts. Different players get separate workspaces.
- **Checkpoint:** immutable full source snapshot with an ID, parent ID, message and timestamp. Broken or incomplete experiments can be checkpointed without running them.
- **Branch:** a named pointer to a checkpoint. Each player initially gets a private `workspace-<UUID>` branch. Creating a branch copies only a pointer, not source files.
- **Active checkpoint:** the source version currently selected to run. Activation does not change a workspace or discard edits.

Source means `plugin.json`, `globals.js`, `commands/**/*.js` and `events/**/*.js`. Runtime JavaScript memory, plugin databases, inventories, world changes and scheduled-task progress are **not** checkpointed.

## In-game commands

| Command | Action |
| --- | --- |
| `/vibe ide` | Select a plugin and edit a `.js` file in a multiline dialog titled with its filename. Save creates a checkpoint; the chat Activate button is separate. |
| `/vibe state <plugin>` | Show your workspace, files, version, branches and active checkpoint. |
| `/vibe checkpoint <plugin> <message>` | Save all workspace edits as a checkpoint. |
| `/vibe branch <plugin> <name>` | Fork your current workspace, including dirty edits, onto a new branch. |
| `/vibe branch <plugin> <name> <checkpoint>` | Create a branch at an existing checkpoint without switching. |
| `/vibe checkout <plugin> <branch>` | Switch a clean workspace to that branch. Checkpoint dirty edits first. |
| `/vibe revisions <plugin>` | List checkpoints across all branches. |
| `/vibe activate <plugin> <checkpoint>` | Validate and activate a checkpoint, preserving workspace edits and later history. |
| `/vibe revert <plugin> <checkpoint>` | Alias for activation. This is **not** a destructive Git reset. |
| `/vibe enable <plugin>` | Reload the selected active checkpoint, or load an untouched legacy disk plugin. Does not publish workspace edits. |

Workspace commands and the dialog use `vibemine.vibe.ide` (operators by default). History uses `vibemine.vibe.plugins.revisions`. Activation accepts `vibemine.vibe.ide`, `vibemine.vibe.plugins.enable`, or `vibemine.vibe.plugins.revert`. Console can list history and activate checkpoints; workspaces require a player.

Source edits are isolated even for players who can start an agent session but cannot activate plugins. Activation also rechecks permission on the server thread. Legacy inline CODE/CODE_PATCH responses require activation permission and are saved as checkpoints before loading.

## Agent workflow

1. `state {action:"status", plugin:"demo"}` opens the workspace and returns `version`, `branch`, `base`, file names and `active` (empty string if none).
2. Read existing files with `file_read`; it returns source and the current workspace version.
3. Call `file_write` or `file_edit` with `expectedVersion` from the latest result. Each successful mutation returns a new version. `state` action `delete` also requires `expectedVersion`.
4. Call `state` action `checkpoint` with `expectedVersion` and a message. The result is the checkpoint ID. Refresh status afterward.
5. When requested, call `state` action `activate` with `checkpoint` and `expectedActive` from status. A stale active pointer fails instead of overwriting another activation.

`state` also supports `history`, `branches`, `branch` (branch name and checkpoint), `checkout` (branch and expectedVersion), and `fork` (new branch name and expectedVersion).

Writing `plugin.json` no longer auto-loads a plugin. Checkpointing and activation are explicit. When a write reports a stale version, read again and reconcile the change; do not blindly retry. If another workspace advanced a shared branch, checkpointing fails without losing edits. Use `fork` to preserve those edits on a new branch, then checkpoint there. Dirty checkout is rejected.

## Activation and recovery

Checkpoint sources and manifest references are resolved from the immutable snapshot, not the live directory. Source preparation runs off the server thread for agent/activation commands. Runtime compilation and command/event registration run on the server thread.

The replacement runtime is initialized before removing the old runtime. A preparation or initialization error leaves the old runtime installed. Registration or active-pointer failure restores its registrations. The old runtime is disposed only after the new runtime and its active pointer are installed. Restart loads selected active checkpoints in dependency order.

**Limit:** initialization executes plugin globals. If globals change the world or an external database and subsequently fail, those side effects cannot be undone. Initialization code should avoid irreversible changes. This is source rollback, not a world transaction.

The original disk source remains unchanged after import. Edit through the workspace tools afterward; editing that old directory will not update an existing workspace or an active checkpoint. Deleting a plugin removes its active selection and legacy directory, but retains SQLite checkpoints and workspaces for recovery. History is retained indefinitely in this version.

Back up `plugin-state.db` with the server stopped, or use SQLite's online backup facility. Do not copy only the main database file while the server is writing: SQLite may also have a `-wal` file.

## Performance and limits

Full snapshots keep reads simple: no delta chains to replay. Branch creation updates one small row. SQLite uses WAL, full durability, transactions and a shared coordinator. Source content is kept in SQLite, so activation does not have to copy a Git working tree or materialize a release directory.

Limits: 128,000 Java characters per file, 256 source files and 2,000,000 characters per workspace. Dialogs have a lower 16,000-character limit; larger source stays editable through agent tools. Each workspace edit rewrites that workspace's source JSON. This intentionally targets small generated plugins; measure database size/write latency before adding blob deduplication or incremental storage. Checkpoints and branch heads are committed together; stale workspace/branch updates are rejected.

No merges, remote sync, signing, delta compression or automatic history deletion are implemented.
