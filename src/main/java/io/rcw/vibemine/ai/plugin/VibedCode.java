package io.rcw.vibemine.ai.plugin;

public interface VibedCode<T extends ExecutionContext> {
    /**
     * @return Source code of event
     */
    String sourceCode();


    /**
     * Used to execute the source code
     */
    void execute(T context);
}
