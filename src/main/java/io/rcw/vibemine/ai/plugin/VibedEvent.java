package io.rcw.vibemine.ai.plugin;

public class VibedEvent implements VibedCode {
    private final String sourceCode;

    public VibedEvent(final String sourceCode) {
        this.sourceCode = sourceCode;
    }

    @Override
    public String sourceCode() {
        return this.sourceCode;
    }

    @Override
    public void execute() {
        // TODO: execute the source code with a given context (Event being called)
    }
}
