package io.rcw.vibemine.ai.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class PluginStateStoreTest {
    @TempDir Path directory;

    private PluginStateStore open() throws IOException {
        return new PluginStateStore(directory.resolve("state.db"), directory.resolve("live"));
    }

    @Test void checkpointsAreImmutableAndSurviveRestartWithoutSigning() throws IOException {
        String checkpoint;
        try (var store = open()) {
            var workspace = store.workspace("demo", "alice");
            workspace = store.write("demo", "alice", workspace.version(), "globals.js", "one\ntwo\n");
            checkpoint = store.checkpoint("demo", "alice", workspace.version(), "First edit");
            var clean = store.workspace("demo", "alice");
            assertFalse(clean.dirty());
            store.write("demo", "alice", clean.version(), "globals.js", "different");
            assertEquals(Map.of("globals.js", "one\ntwo\n"), store.snapshot("demo", checkpoint));
            assertFalse(Files.exists(directory.resolve("live/demo")));
        }
        try (var store = open()) {
            assertEquals("different", store.workspace("demo", "alice").files().get("globals.js"));
            assertEquals("one\ntwo\n", store.snapshot("demo", checkpoint).get("globals.js"));
            assertEquals(checkpoint, store.history("demo", 100).getFirst().id());
        }
    }

    @Test void workspacesAreIsolatedAndStaleEditsFail() throws IOException {
        try (var store = open()) {
            var alice = store.workspace("demo", "alice");
            store.workspace("demo", "bob");
            var changed = store.write("demo", "alice", alice.version(), "globals.js", "first");
            assertTrue(store.workspace("demo", "bob").files().isEmpty());
            assertEquals(java.util.List.of("demo"), store.workspacePlugins("alice"));
            assertTrue(store.workspacePlugins("charlie").isEmpty());
            assertTrue(store.activePlugins().isEmpty());
            assertThrows(IOException.class, () -> store.write("demo", "alice", alice.version(), "globals.js", "lost update"));
            assertThrows(IOException.class, () -> store.checkpoint("demo", "alice", alice.version(), "stale"));
            assertEquals("first", store.workspace("demo", "alice").files().get("globals.js"));
            assertEquals(changed.version(), store.workspace("demo", "alice").version());
        }
    }

    @Test void branchSwitchPreservesHistoryAndRejectsDirtyWorkspace() throws IOException {
        try (var store = open()) {
            var workspace = store.workspace("demo", "alice");
            workspace = store.write("demo", "alice", workspace.version(), "globals.js", "first");
            String first = store.checkpoint("demo", "alice", workspace.version(), "First");
            store.branch("demo", "experiment", first);
            workspace = store.workspace("demo", "alice");
            workspace = store.write("demo", "alice", workspace.version(), "globals.js", "second");
            long dirtyVersion = workspace.version();
            assertThrows(IOException.class, () -> store.checkout("demo", "alice", dirtyVersion, "experiment"));
            String second = store.checkpoint("demo", "alice", dirtyVersion, "Second");
            workspace = store.checkout("demo", "alice", store.workspace("demo", "alice").version(), "experiment");
            assertEquals("first", workspace.files().get("globals.js"));
            assertEquals("second", store.snapshot("demo", second).get("globals.js"));
            assertEquals(first, store.history("demo", 100).getFirst().parent());
            assertEquals(2, store.history("demo", 100).size());
        }
    }

    @Test void concurrentBranchAdvancesRequireForkAndKeepDirtyEdits() throws IOException {
        try (var store = open()) {
            var alice = store.workspace("demo", "alice");
            alice = store.write("demo", "alice", alice.version(), "globals.js", "initial");
            String initial = store.checkpoint("demo", "alice", alice.version(), "Initial");
            store.branch("demo", "shared", initial);
            alice = store.checkout("demo", "alice", store.workspace("demo", "alice").version(), "shared");
            var bob = store.workspace("demo", "bob");
            bob = store.checkout("demo", "bob", bob.version(), "shared");
            alice = store.write("demo", "alice", alice.version(), "globals.js", "alice");
            String aliceCheckpoint = store.checkpoint("demo", "alice", alice.version(), "Alice edit");
            bob = store.write("demo", "bob", bob.version(), "globals.js", "bob");
            long version = bob.version();
            assertThrows(IOException.class, () -> store.checkpoint("demo", "bob", version, "Bob edit"));
            assertEquals(aliceCheckpoint, store.branches("demo").get("shared"));
            bob = store.fork("demo", "bob", version, "bob-fix");
            String bobCheckpoint = store.checkpoint("demo", "bob", bob.version(), "Bob edit");
            assertEquals("bob", store.snapshot("demo", bobCheckpoint).get("globals.js"));
            assertEquals(initial, store.history("demo", 100).getFirst().parent());
            assertEquals(aliceCheckpoint, store.branches("demo").get("shared"));
        }
    }

    @Test void activationPointerRejectsStaleOrForeignCheckpoints() throws IOException {
        try (var store = open()) {
            store.workspace("demo", "alice");
            var workspace = store.write("demo", "alice", 0, "globals.js", "first");
            String first = store.checkpoint("demo", "alice", workspace.version(), "First");
            workspace = store.write("demo", "alice", store.workspace("demo", "alice").version(), "globals.js", "second");
            String second = store.checkpoint("demo", "alice", workspace.version(), "Second");
            store.markActive("demo", first, null);
            assertThrows(IOException.class, () -> store.markActive("demo", second, null));
            assertThrows(IOException.class, () -> store.markActive("other", first, null));
            assertEquals(first, store.active("demo"));
            store.markActive("demo", second, first);
            assertEquals(second, store.active("demo"));
            assertEquals(2, store.history("demo", 100).size());
        }
    }

    @Test void materializeWritesOnlyCheckpointFilesToLiveDirectory() throws IOException {
        try (var store = open()) {
            var workspace = store.workspace("demo", "alice");
            workspace = store.write("demo", "alice", workspace.version(), "plugin.json", "{\"name\":\"demo\"}");
            workspace = store.write("demo", "alice", workspace.version(), "commands/demo.js", "command");
            String checkpoint = store.checkpoint("demo", "alice", workspace.version(), "Ready");
            Files.createDirectories(directory.resolve("live/demo"));
            Files.writeString(directory.resolve("live/demo/stale.txt"), "stale");

            store.materialize("demo", checkpoint);

            assertEquals("{\"name\":\"demo\"}", Files.readString(directory.resolve("live/demo/plugin.json")));
            assertEquals("command", Files.readString(directory.resolve("live/demo/commands/demo.js")));
            assertFalse(Files.exists(directory.resolve("live/demo/stale.txt")));
        }
    }

    @Test void importsSourceOnlyAndNeverChangesOriginalFiles() throws IOException {
        Path live = directory.resolve("live/demo");
        Files.createDirectories(live.resolve(".git"));
        Files.writeString(live.resolve("plugin.json"), "{\"name\":\"demo\"}");
        Files.writeString(live.resolve("globals.js"), "original");
        Files.writeString(live.resolve("data.db"), "not source");
        Files.writeString(live.resolve(".git/config"), "commit.gpgsign=true");
        try (var store = open()) {
            var workspace = store.workspace("demo", "alice");
            assertEquals(2, workspace.files().size());
            assertNotNull(store.active("demo"));
            store.write("demo", "alice", workspace.version(), "globals.js", "new");
            assertEquals("original", Files.readString(live.resolve("globals.js")));
            assertEquals("commit.gpgsign=true", Files.readString(live.resolve(".git/config")));
        }
    }

    @Test void validatesPathsLimitsAndUniqueEditsAndRollsBackFailedOperations() throws IOException {
        try (var store = open()) {
            store.workspace("demo", "alice");
            assertThrows(IllegalArgumentException.class, () -> store.workspace("../escape", "alice"));
            for (String path : new String[]{"../globals.js", "/globals.js", ".git/config", "data.db", "commands/../globals.js"}) {
                assertThrows(IllegalArgumentException.class, () -> store.write("demo", "alice", 0, path, "x"));
            }
            assertThrows(IllegalArgumentException.class, () -> store.write("demo", "alice", 0, "globals.js", "x".repeat(PluginStateStore.MAX_FILE + 1)));
            var workspace = store.write("demo", "alice", 0, "globals.js", "same same");
            long version = workspace.version();
            assertThrows(IOException.class, () -> store.edit("demo", "alice", version, "globals.js", "same", "wrong"));
            assertEquals(version, store.workspace("demo", "alice").version());
            workspace = store.edit("demo", "alice", version, "globals.js", "same same", "one\ntwo");
            workspace = store.delete("demo", "alice", workspace.version(), "globals.js");
            assertTrue(workspace.files().isEmpty());
        }
    }

    @Test void simultaneousWritersCannotBothUseTheSameVersion() throws Exception {
        try (var store = open(); var executor = Executors.newFixedThreadPool(2)) {
            store.workspace("demo", "alice");
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> writer = () -> {
                start.await();
                try { store.write("demo", "alice", 0, "globals.js", "edit"); return true; }
                catch (IOException conflict) { return false; }
            };
            var first = executor.submit(writer);
            var second = executor.submit(writer);
            start.countDown();
            assertNotEquals(first.get(), second.get());
            assertEquals(1, store.workspace("demo", "alice").version());
        }
    }

    @Test void rejectsSymlinkSourceImports() throws IOException {
        Path live = directory.resolve("live/demo");
        Files.createDirectories(live);
        Path outside = directory.resolve("secret.js");
        Files.writeString(outside, "secret");
        Files.createSymbolicLink(live.resolve("globals.js"), outside);
        try (var store = open()) {
            assertThrows(IOException.class, () -> store.workspace("demo", "alice"));
            assertTrue(store.history("demo", 100).isEmpty());
            assertNull(store.active("demo"));
        }
    }
}
