# Multi-language runtime and code-host tooling plan

Branch: `plan/multi-language-tooling`

## Goals

1. Add first-class highlighting support for Ruby and Python, alongside existing JavaScript and JSON support.
2. Prepare VibePlugin internals for multiple implementation languages instead of assuming generated plugins are always JavaScript JSON payloads.
3. Add an extensible tooling layer for publishing/exporting generated plugins to code hosts such as GitHub.
4. Keep existing JavaScript plugin behavior compatible while the new model is introduced incrementally.

## Current state

- Generated plugins are described by `VibedPluginSchema` and persisted/loaded as structured JSON.
- Runtime execution is JavaScript-only through GraalJS.
- Syntax highlighting is Tree-sitter based and currently supports JavaScript and JSON:
  - `src/main/java/io/rcw/vibemine/code/Language.java`
  - `src/main/java/io/rcw/vibemine/code/HighlightQueries.java`
  - `src/main/java/io/rcw/vibemine/ai/tools/syntax/SyntaxHighlightTool.java`
- Tool calls already exist under `src/main/java/io/rcw/vibemine/ai/tools`, so code-host tooling can follow the same pattern.

## Phase 1: Ruby and Python syntax highlighting

### Dependencies

Add Tree-sitter grammar dependencies in `build.gradle.kts` after the existing JavaScript/JSON grammar dependencies:

- `io.github.bonede:tree-sitter-python:<compatible-version>`
- `io.github.bonede:tree-sitter-ruby:<compatible-version>`

Use versions compatible with `io.github.bonede:tree-sitter:0.26.6`. Confirm exact artifact versions from Maven Central before implementation.

### Code changes

1. Update `Language.java`:
   - Import `TreeSitterPython` and `TreeSitterRuby`.
   - Add enum values `PYTHON` and `RUBY` with their highlight queries.
2. Update `HighlightQueries.java`:
   - Add `PYTHON_HIGHLIGHT_QUERY`.
   - Add `RUBY_HIGHLIGHT_QUERY`.
   - Start with pragmatic captures for strings, numbers, comments, constants, keywords, operators, class/function/method identifiers.
3. Update `SyntaxHighlightTool.java`:
   - Accept aliases: `python`, `py`, `ruby`, `rb`.
   - Update unsupported-language and usage text.
4. Add tests for language parsing/highlighting if the current test setup can instantiate Tree-sitter parsers reliably.

### Acceptance criteria

- `./gradlew test` passes.
- `./gradlew build` passes and the shaded jar still includes Tree-sitter language services correctly.
- `syntax_highlight` returns Adventure component JSON for JavaScript, JSON, Python, and Ruby snippets.

## Phase 2: Introduce a language-aware plugin model

### Proposed schema evolution

Keep existing JSON compatibility, but add language metadata and split source representation from plugin metadata.

Candidate schema fields:

```json
{
  "schemaVersion": 2,
  "name": "example_plugin",
  "description": "...",
  "version": 1,
  "language": "javascript",
  "entrypoint": "main.js",
  "files": [
    { "path": "main.js", "language": "javascript", "content": "..." },
    { "path": "README.md", "language": "markdown", "content": "..." }
  ],
  "commands": [],
  "events": [],
  "permissions": [],
  "metadata": {}
}
```

Notes:

- `language` is the runtime language for executable plugin code.
- `files[]` allows export to real repositories without inventing files from JSON later.
- JavaScript remains the only executable runtime initially.
- Python/Ruby can be highlight/export-only until a runtime strategy is chosen.
- A `schemaVersion` field enables migration from the current `VibedPluginSchema` shape.

### Implementation steps

1. Add a new schema package or records for `VibePluginManifest`, `VibePluginFile`, and `VibePluginLanguage`.
2. Add adapters that convert current `VibedPluginSchema` JSON into the new manifest model.
3. Keep `VibedPluginManager` loading old plugins unchanged until the new manifest path is tested.
4. Update the AI system prompt to explicitly emit JavaScript runtime plugins for now, but allow extra support files when export tooling is requested.
5. Add validation:
   - Safe relative file paths only.
   - No path traversal.
   - Known language identifiers.
   - Entrypoint must exist.
   - Runtime language must be supported by an installed runtime.

### Runtime decision points

Before enabling Python/Ruby execution, choose one:

- JVM-hosted runtimes, if compatible and sandboxable.
- External process execution, likely not recommended for public servers without strong isolation.
- Transpile/compile to JavaScript, likely fragile for plugin APIs.
- Highlight/export-only support for non-JavaScript languages.

Recommendation: ship Ruby/Python highlighting and repository export first; defer execution until a separate sandbox design exists.

## Phase 3: Code-host/export tooling foundation

### Tool abstraction

Add a host-agnostic publishing service:

- `CodeHostProvider`
  - `id()`
  - `validateConfig()`
  - `createRepository(...)`
  - `upsertFiles(...)`
  - `createGistOrSnippet(...)` optional
  - `openPullRequest(...)` optional
- `CodeHostPublishRequest`
  - plugin id/name
  - target host
  - visibility
  - repo owner/name
  - branch
  - commit message
  - files
- `CodeHostPublishResult`
  - URL
  - commit SHA or host id
  - warnings/errors

### First provider: GitHub

Add a GitHub provider after the interface is stable.

Config proposal in `config.yml`:

```yaml
code_hosts:
  github:
    enabled: false
    token: ""
    default_owner: ""
    default_visibility: "private"
```

Implementation choices:

- Start with GitHub REST API via Java HTTP client to avoid a large SDK dependency.
- Use fine-grained tokens with repository/content permissions.
- Never expose tokens to AI tool output or player chat.
- Permission-gate publishing commands/tools to operators.

### AI tool

Add `publish_plugin` or `export_plugin` under `src/main/java/io/rcw/vibemine/ai/tools/codehost`.

Possible first version:

```json
{
  "pluginName": "tree_wand",
  "host": "github",
  "mode": "gist|repository|pull_request",
  "owner": "optional-owner",
  "repository": "optional-repo",
  "branch": "optional-branch",
  "visibility": "private",
  "commitMessage": "Export tree_wand from VibeMine"
}
```

The tool should gather files from the manifest/export adapter rather than trusting arbitrary model-provided file content at publish time.

### Commands

Add operator-facing commands after service exists:

- `/vibe export <plugin>`: write a local folder/archive representation.
- `/vibe publish <plugin> github [repo]`: publish to GitHub.
- `/vibe publish-status <job-id>` if publishing is async.

### Acceptance criteria

- Existing plugins can be exported to a deterministic file tree.
- Publishing can be dry-run locally without credentials.
- GitHub publishing works with a configured token and returns a repository/file URL.
- Tokens are never logged or returned in tool output.

## Phase 4: Migration and compatibility

1. Add schema migration from current JSON shape to manifest v2.
2. Store original JSON for rollback until migration has been exercised.
3. Support both old and new records in SQLite for at least one release.
4. Add tests for old-schema load, new-schema load, export generation, and validation failures.
5. Document the new generated plugin file layout in `README.md`.

## Suggested implementation order

1. Ruby/Python Tree-sitter dependencies and query constants.
2. Syntax tool alias updates and tests.
3. Manifest records and validation, with current JSON-to-manifest adapter.
4. Local export service that writes a file tree or zip.
5. Code-host provider interfaces and dry-run provider.
6. GitHub provider.
7. AI tool and operator commands for export/publish.
8. Persistence migration.

## Open questions

- Should Ruby/Python be executable or only highlighted/exported in the near term?
- Should generated plugin storage remain in SQLite as JSON blobs, or move toward file trees with a manifest?
- Should publishing be fully AI-tool callable, command-only, or both?
- Should GitHub support repositories first, gists first, or both?
- What permission should control exporting/publishing generated plugins?
