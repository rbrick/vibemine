package io.rcw.vibemine.ai.plugin.runtime.dsl;

import java.util.ArrayList;
import java.util.List;

public final class Tokenizer {
    private final String source;
    private int pos;

    public Tokenizer(String source) { this.source = source == null ? "" : source; }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (!eof()) {
            char c = peek();
            if (Character.isWhitespace(c)) { pos++; continue; }
            if (c == '"' || c == '\'') { tokens.add(readString(c)); continue; }
            if (Character.isDigit(c) || (c == '.' && pos + 1 < source.length() && Character.isDigit(source.charAt(pos + 1)))) { tokens.add(readNumber()); continue; }
            if (isIdentifierStart(c)) { tokens.add(readIdentifier()); continue; }
            if ("(),?:".indexOf(c) >= 0) { tokens.add(new Token(TokenType.PUNCTUATION, String.valueOf(c), 0, pos++)); continue; }
            tokens.add(readOperator());
        }
        tokens.add(Token.eof(pos));
        return tokens;
    }

    private Token readString(char quote) {
        int start = pos++;
        StringBuilder sb = new StringBuilder();
        while (!eof()) {
            char c = source.charAt(pos++);
            if (c == quote) return new Token(TokenType.STRING, sb.toString(), 0, start);
            if (c == '\\') {
                if (eof()) throw error("unterminated escape", start);
                char e = source.charAt(pos++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    case '\\' -> sb.append('\\');
                    default -> throw error("unsupported escape sequence: \\" + e, pos - 2);
                }
            } else sb.append(c);
        }
        throw error("unterminated string", start);
    }

    private Token readNumber() {
        int start = pos;
        boolean decimal = false, lastSep = false;
        StringBuilder parsed = new StringBuilder();
        while (!eof()) {
            char c = peek();
            if (Character.isDigit(c)) { parsed.append(c); pos++; lastSep = false; }
            else if (c == '_') {
                if (lastSep) throw error("invalid numeric separator", pos);
                if (pos + 1 >= source.length() || !Character.isDigit(source.charAt(pos + 1))) break;
                pos++;
                lastSep = true;
            }
            else if (c == '.') { if (decimal) throw error("invalid numeric decimal", pos); decimal = true; parsed.append(c); pos++; lastSep = false; }
            else break;
        }
        return new Token(TokenType.NUMBER, source.substring(start, pos), Double.parseDouble(parsed.toString()), start);
    }

    private Token readIdentifier() {
        int start = pos++;
        while (!eof() && isIdentifierPart(peek())) pos++;
        return new Token(TokenType.IDENTIFIER, source.substring(start, pos), 0, start);
    }

    private Token readOperator() {
        int start = pos;
        if (pos + 1 < source.length()) {
            String two = source.substring(pos, pos + 2);
            if (List.of("==", "!=", "<=", ">=", "&&", "||").contains(two)) { pos += 2; return new Token(TokenType.OPERATOR, two, 0, start); }
        }
        char c = source.charAt(pos++);
        if ("+-*/%<>!".indexOf(c) >= 0) return new Token(TokenType.OPERATOR, String.valueOf(c), 0, start);
        throw error("unexpected character: " + c, start);
    }

    private boolean isIdentifierStart(char c) { return Character.isLetter(c) || c == '_' || c == '$' || c == '@'; }
    private boolean isIdentifierPart(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '@' || c == '.' || c == '[' || c == ']'; }
    private char peek() { return source.charAt(pos); }
    private boolean eof() { return pos >= source.length(); }
    private DslException error(String message, int at) { return new DslException(message + " at " + at); }
}
