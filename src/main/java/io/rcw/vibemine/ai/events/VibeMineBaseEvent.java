package io.rcw.vibemine.ai.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public abstract class VibeMineBaseEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public VibeMineBaseEvent(boolean async) {
        super(async);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}
