package io.rcw.vibemine.ai.plugin.runtime;

import io.rcw.vibemine.ai.plugin.VibedPluginManager;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * Dynamic JavaScript module proxy for one imported vibed plugin's exports.
 */
public final class VibePluginModule implements ProxyObject {
    private final VibedPluginManager manager;
    private final String requester;
    private final String target;

    public VibePluginModule(VibedPluginManager manager, String requester, String target) {
        this.manager = manager;
        this.requester = requester;
        this.target = target;
    }

    public void emit(String eventName, Object payload) {
        manager.emitPluginEvent(requester, target, eventName, payload);
    }

    @Override
    public Object getMember(String key) {
        manager.requireImport(requester, target);
        if (key.equals("emit")) {
            return (ProxyExecutable) arguments -> {
                Object payload = arguments.length > 1 ? arguments[1] : null;
                manager.emitPluginEvent(requester, target, arguments[0].asString(), payload);
                return null;
            };
        }
        if (!manager.exportNames(target).contains(key)) return null;
        return (ProxyExecutable) arguments -> manager.callExport(target, key, arguments);
    }

    @Override
    public Object getMemberKeys() {
        java.util.List<String> keys = new java.util.ArrayList<>(manager.exportNames(target));
        keys.add("emit");
        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return key.equals("emit") || manager.exportNames(target).contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Imported plugin modules are read-only");
    }

    @Override
    public boolean removeMember(String key) {
        throw new UnsupportedOperationException("Imported plugin modules are read-only");
    }
}
