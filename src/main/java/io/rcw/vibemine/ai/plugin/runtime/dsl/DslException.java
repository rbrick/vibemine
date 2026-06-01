package io.rcw.vibemine.ai.plugin.runtime.dsl;

public class DslException extends RuntimeException {
    public DslException(String message) { super(message); }
    public DslException(String message, Throwable cause) { super(message, cause); }
}
