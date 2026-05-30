package io.rcw.vibemine.ai.plugin.runtime;

import io.rcw.vibemine.ai.plugin.VibedPluginManager;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;

/**
 * JavaScript binding for importing explicitly exported APIs from other vibed plugins.
 */
public final class VibePluginRegistry implements ProxyObject {
    private final VibedPluginManager manager;
    private final String requester;

    public VibePluginRegistry(VibedPluginManager manager, String requester) {
        this.manager = manager;
        this.requester = requester;
    }

    public boolean has(String name) {
        return manager.isEnabled(name);
    }

    public boolean enabled(String name) {
        return has(name);
    }

    public List<String> names() {
        return manager.enabledPluginNames();
    }

    public VibePluginModule importPlugin(String name) {
        return import_(name);
    }

    public VibePluginModule import_(String name) {
        manager.requireImport(requester, name);
        return new VibePluginModule(manager, requester, name);
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "import", "importPlugin", "import_" -> (ProxyExecutable) arguments -> import_(arguments[0].asString());
            case "has", "enabled" -> (ProxyExecutable) arguments -> has(arguments[0].asString());
            case "names" -> (ProxyExecutable) arguments -> names();
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return new String[] {"import", "importPlugin", "import_", "has", "enabled", "names"};
    }

    @Override
    public boolean hasMember(String key) {
        return List.of("import", "importPlugin", "import_", "has", "enabled", "names").contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Plugin registry is read-only");
    }

    @Override
    public boolean removeMember(String key) {
        throw new UnsupportedOperationException("Plugin registry is read-only");
    }
}
