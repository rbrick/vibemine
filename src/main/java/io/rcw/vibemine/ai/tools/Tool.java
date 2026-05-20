package io.rcw.vibemine.ai.tools;

import org.bukkit.entity.Player;

public interface Tool<T, R> {
    Class<T> inputClass();

    Class<R> outputClass();

    R execute(final Player player, T t);
}
