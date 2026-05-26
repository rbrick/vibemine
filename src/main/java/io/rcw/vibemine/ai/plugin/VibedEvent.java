package io.rcw.vibemine.ai.plugin;

import io.rcw.vibemine.ai.plugin.context.EventExecutionContext;
import org.bukkit.event.Event;
import org.graalvm.polyglot.Value;

public final class VibedEvent implements VibedCode<Event> {
    private final VibedPlugin plugin;
    private final String eventName;
    private final String sourceCode;

    public VibedEvent(VibedPlugin plugin, String eventName, String sourceCode) {
        this.plugin = plugin;
        this.eventName = eventName;
        this.sourceCode = sourceCode;
    }

    public String eventName() { return eventName; }

    @Override
    public String sourceCode() {
        return sourceCode;
    }

    @Override
    public void execute(Event event) {
        executeSource(event, null);
    }

    void executeSource(Event event, Value state) {
        plugin.evalFunction(sourceCode).execute(new EventExecutionContext(eventName, event), state);
    }
}
