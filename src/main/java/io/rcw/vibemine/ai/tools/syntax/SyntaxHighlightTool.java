package io.rcw.vibemine.ai.tools.syntax;

import io.rcw.vibemine.ai.tools.Tool;
import io.rcw.vibemine.annotations.Named;
import io.rcw.vibemine.code.Highlighting;
import io.rcw.vibemine.code.Language;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;

import java.util.Locale;

@Named("syntax_highlight")
public final class SyntaxHighlightTool implements Tool<SyntaxHighlightTool.SyntaxHighlightInput, SyntaxHighlightTool.SyntaxHighlightOutput> {

    public record SyntaxHighlightInput(String language, String code) { }

    public record SyntaxHighlightOutput(String language, String componentJson, String error) {
        public static SyntaxHighlightOutput error(String language, String message) {
            return new SyntaxHighlightOutput(language, null, message);
        }
    }

    @Override
    public Class<SyntaxHighlightInput> inputClass() {
        return SyntaxHighlightInput.class;
    }

    @Override
    public Class<SyntaxHighlightOutput> outputClass() {
        return SyntaxHighlightOutput.class;
    }

    @Override
    public SyntaxHighlightOutput execute(Player player, SyntaxHighlightInput input) {
        if (input == null) return SyntaxHighlightOutput.error("", "Missing input");
        if (input.code() == null || input.code().isBlank()) return SyntaxHighlightOutput.error(input.language(), "Missing code");

        Language language = parseLanguage(input.language());
        if (language == null) {
            return SyntaxHighlightOutput.error(input.language(), "Unsupported language. Supported languages: javascript, js, json");
        }

        try {
            var highlighted = Highlighting.highlight(language, input.code());
            var componentJson = GsonComponentSerializer.gson().serialize(highlighted);
            return new SyntaxHighlightOutput(language.name().toLowerCase(Locale.ROOT), componentJson, null);
        } catch (Exception exception) {
            return SyntaxHighlightOutput.error(input.language(), exception.getMessage());
        }
    }

    private Language parseLanguage(String language) {
        if (language == null || language.isBlank()) return Language.JAVASCRIPT;
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "javascript", "js" -> Language.JAVASCRIPT;
            case "json" -> Language.JSON;
            default -> null;
        };
    }

    @Override
    public String usage() {
        return """
                Highlight source code using the server's Highlighting utility.
                Input: {"language":"javascript|js|json", "code":"source code"}
                Output returns componentJson, an Adventure JSON chat component. Use send_message to send it to the player.
                """;
    }
}
