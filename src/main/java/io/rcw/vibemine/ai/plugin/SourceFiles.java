package io.rcw.vibemine.ai.plugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class SourceFiles {
    public static final int MAX_LENGTH = 16000;
    private final Path root;

    public SourceFiles(Path root) {
        this.root = root;
    }

    private Path resolve(String plugin, String file) throws IOException {
        Path base = root.toRealPath();
        Path directory = base.resolve(plugin).normalize();
        if (!directory.getParent().equals(base) || Files.isSymbolicLink(directory)) {
            throw new IOException("Invalid plugin directory");
        }
        Path path = directory.resolve(file).normalize();
        if (!path.startsWith(directory) || !file.endsWith(".js") || !Files.isRegularFile(path)
                || !path.toRealPath().equals(path)) {
            throw new IOException("Not a source file inside this plugin");
        }
        return path;
    }

    public List<String> list(String plugin) throws IOException {
        Path directory = root.toAbsolutePath().normalize().resolve(plugin).normalize();
        if (!directory.getParent().equals(root.toAbsolutePath().normalize()) || Files.isSymbolicLink(directory)) {
            throw new IOException("Invalid plugin directory");
        }
        try (var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".js"))
                    .map(directory::relativize).map(Path::toString).sorted().toList();
        }
    }

    public String read(String plugin, String file) throws IOException {
        Path path = resolve(plugin, file);
        if (Files.size(path) > MAX_LENGTH * 4L) throw new IOException("File is too large for the dialog editor");
        String text = Files.readString(path);
        if (text.length() > MAX_LENGTH) throw new IOException("File is too large for the dialog editor");
        return text;
    }

    public synchronized void save(String plugin, String file, String original, String text) throws IOException {
        if (text == null || text.length() > MAX_LENGTH) throw new IOException("Invalid source text");
        Path path = resolve(plugin, file);
        if (!Files.readString(path).equals(original)) throw new IOException("File changed; reopen it before saving");
        Path temporary = Files.createTempFile(path.getParent(), ".ide-", ".tmp");
        try {
            Files.writeString(temporary, text);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
