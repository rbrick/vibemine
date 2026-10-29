package io.rcw.vibemine.ai.plugin;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeSwapTest {
    private static final class Runtime implements RuntimeSwap.Runtime {
        boolean enabled;
        boolean fail;
        @Override public void enable() { enabled = true; if (fail) throw new IllegalArgumentException("Bad JavaScript"); }
        @Override public void disable() { enabled = false; }
    }

    @Test void failedInitializationNeverDetachesOldRuntime() {
        Runtime previous = new Runtime();
        previous.enable();
        Runtime candidate = new Runtime();
        candidate.fail = true;
        AtomicReference<Runtime> registered = new AtomicReference<>(previous);
        assertThrows(IllegalArgumentException.class, () -> RuntimeSwap.install(previous, candidate,
                registered::set, runtime -> registered.compareAndSet(runtime, null),
                () -> fail("Must not publish failed candidate")));
        assertSame(previous, registered.get());
        assertTrue(previous.enabled);
        assertFalse(candidate.enabled);
    }

    @Test void failedRegistrationRestoresOldRuntime() {
        Runtime previous = new Runtime();
        previous.enable();
        Runtime candidate = new Runtime();
        AtomicReference<Runtime> registered = new AtomicReference<>(previous);
        assertThrows(IllegalStateException.class, () -> RuntimeSwap.install(previous, candidate, runtime -> {
            registered.set(runtime);
            if (runtime == candidate) throw new IllegalStateException("Registration failed");
        }, runtime -> registered.compareAndSet(runtime, null), () -> fail("Must not publish")));
        assertSame(previous, registered.get());
        assertTrue(previous.enabled);
        assertFalse(candidate.enabled);
    }

    @Test void failedDurableActivationRestoresOldRuntime() {
        Runtime previous = new Runtime();
        previous.enable();
        Runtime candidate = new Runtime();
        AtomicReference<Runtime> registered = new AtomicReference<>(previous);
        assertThrows(IOException.class, () -> RuntimeSwap.install(previous, candidate, registered::set,
                runtime -> registered.compareAndSet(runtime, null), () -> { throw new IOException("Stale activation"); }));
        assertSame(previous, registered.get());
        assertTrue(previous.enabled);
        assertFalse(candidate.enabled);
    }

    @Test void successPublishesOnlyAfterRegistrationAndBeforeOldRuntimeIsDisposed() throws IOException {
        Runtime previous = new Runtime();
        previous.enable();
        Runtime candidate = new Runtime();
        AtomicReference<Runtime> registered = new AtomicReference<>(previous);
        RuntimeSwap.install(previous, candidate, registered::set, runtime -> registered.compareAndSet(runtime, null), () -> {
            assertSame(candidate, registered.get());
            assertTrue(candidate.enabled);
            assertTrue(previous.enabled);
        });
        previous.disable();
        assertSame(candidate, registered.get());
        assertTrue(candidate.enabled);
        assertFalse(previous.enabled);
    }

    @Test void failedFirstActivationLeavesNoRuntimeInstalled() {
        Runtime candidate = new Runtime();
        AtomicReference<Runtime> registered = new AtomicReference<>();
        assertThrows(IOException.class, () -> RuntimeSwap.install(null, candidate, registered::set,
                runtime -> registered.compareAndSet(runtime, null), () -> { throw new IOException("Disk full"); }));
        assertNull(registered.get());
        assertFalse(candidate.enabled);
    }
}
