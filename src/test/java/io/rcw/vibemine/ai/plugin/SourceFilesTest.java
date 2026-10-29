package io.rcw.vibemine.ai.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SourceFilesTest {
    @TempDir Path root;

    @Test void savesMultilineAndRejectsStaleEdits() throws IOException {
        Files.createDirectory(root.resolve("demo"));
        Path file = root.resolve("demo/main.js");
        Files.writeString(file, "old");
        SourceFiles sources = new SourceFiles(root);
        assertEquals(java.util.List.of("main.js"), sources.list("demo"));
        assertEquals("old", sources.read("demo", "main.js"));
        sources.save("demo", "main.js", "old", "one\ntwo\n");
        assertEquals("one\ntwo\n", Files.readString(file));
        assertThrows(IOException.class, () -> sources.save("demo", "main.js", "old", "lost"));
        assertEquals("one\ntwo\n", Files.readString(file));
    }

    @Test void rejectsEscapesSymlinksAndOversizedFiles() throws IOException {
        Files.createDirectory(root.resolve("demo"));
        Files.writeString(root.resolve("outside.js"), "secret");
        Files.createSymbolicLink(root.resolve("demo/link.js"), root.resolve("outside.js"));
        SourceFiles sources = new SourceFiles(root);
        assertThrows(IOException.class, () -> sources.read("demo", "../outside.js"));
        assertThrows(IOException.class, () -> sources.read("demo", "link.js"));
        assertThrows(IOException.class, () -> sources.list("../"));
        Files.writeString(root.resolve("demo/large.js"), "x".repeat(SourceFiles.MAX_LENGTH + 1));
        assertThrows(IOException.class, () -> sources.read("demo", "large.js"));
        assertThrows(IOException.class, () -> sources.save("demo", "large.js", "", null));
    }
}
