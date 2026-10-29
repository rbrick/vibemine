package io.rcw.vibemine.ai.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

public final class CredentialFiles {
    private CredentialFiles() {}
    public static void write(Path path, String text) throws IOException {
        path = path.toAbsolutePath().normalize();
        if (!Files.getFileStore(path.getParent()).supportsFileAttributeView("posix")) throw new IOException("Protected credential storage currently requires POSIX file permissions");
        if (Files.isSymbolicLink(path)) throw new IOException("Credential symlinks are not allowed");
        Path temporary = Files.createTempFile(path.getParent(), ".credentials-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    public static String read(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 64000) throw new IOException("Invalid credential file");
        if (!Files.getFileStore(path).supportsFileAttributeView("posix")) throw new IOException("Protected credential storage currently requires POSIX file permissions");
        var permissions = Files.getPosixFilePermissions(path);
        if (permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_"))) {
            throw new IOException("Credential file must be owner-only; run chmod 600 on it");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
