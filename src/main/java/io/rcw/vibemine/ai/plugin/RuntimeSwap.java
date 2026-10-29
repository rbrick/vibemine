package io.rcw.vibemine.ai.plugin;

import java.io.IOException;
import java.util.function.Consumer;

/** Keeps the previous runtime alive until registration and durable activation both succeed. */
final class RuntimeSwap {
    private RuntimeSwap() {}
    interface Runtime { void enable(); void disable(); }
    @FunctionalInterface interface Commit { void run() throws IOException; }

    static <T extends Runtime> void install(T previous, T candidate, Consumer<T> attach, Consumer<T> detach, Commit commit) throws IOException {
        try {
            candidate.enable();
        } catch (RuntimeException exception) {
            dispose(candidate, exception);
            throw exception;
        }
        try {
            if (previous != null) detach.accept(previous);
            attach.accept(candidate);
            commit.run();
        } catch (RuntimeException | IOException exception) {
            try { detach.accept(candidate); }
            catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            if (previous != null) {
                try { attach.accept(previous); }
                catch (RuntimeException restore) { exception.addSuppressed(restore); }
            }
            dispose(candidate, exception);
            throw exception;
        }
    }

    private static void dispose(Runtime candidate, Exception failure) {
        try { candidate.disable(); }
        catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
    }
}
