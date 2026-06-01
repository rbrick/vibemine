package io.rcw.vibemine.ai.plugin.runtime.dsl;

public record Token(TokenType type, String literal, double number, int position) {
    public static Token eof(int position) { return new Token(TokenType.EOF, "", 0, position); }
}
