package io.rcw.vibemine.code;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.NonNull;
import org.treesitter.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class Highlighting {

    private static final Highlight ZERO =  new Highlight("","", 0, 0, TextColor.color(0x0));

    record Highlight(String text, String kind, int start, int end, TextColor color) { }

    record Theme(Map<String, TextColor> colors) {
        TextColor colorFor(final String name) {
            return  colors.getOrDefault(name, NamedTextColor.WHITE);
        }
    }

    // Colors taken from dracula theme
    static Theme DEFAULT_THEME = new Theme(Map.of(
            "keyword", TextColor.color(0xffb86c), // Keyword color
            "constant.builtin", TextColor.color(0xffb86c), // constant color
            "string", TextColor.color(0x50fa7b), // string color
            "number", TextColor.color(0x8be9fd),
            "function", TextColor.color(0xbd93f9),
            "method", TextColor.color(0xbd93f9)
    ));


    public static Component json(final String code) {
        return highlight(Language.JSON, code);
    }

    public static Component javascript(final String code) {
        return highlight(Language.JAVASCRIPT, code);
    }

    public static Component diff(final String before, final String after) {
        String[] oldLines = before == null || before.isEmpty() ? new String[0] : before.split("\\R", -1);
        String[] newLines = after == null || after.isEmpty() ? new String[0] : after.split("\\R", -1);
        int[][] lcs = new int[oldLines.length + 1][newLines.length + 1];
        for (int i = oldLines.length - 1; i >= 0; i--) {
            for (int j = newLines.length - 1; j >= 0; j--) {
                lcs[i][j] = oldLines[i].equals(newLines[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<Component> lines = new ArrayList<>();
        int i = 0, j = 0;
        while (i < oldLines.length || j < newLines.length) {
            if (i < oldLines.length && j < newLines.length && oldLines[i].equals(newLines[j])) {
                lines.add(diffLine("  ", NamedTextColor.DARK_GRAY, oldLines[i], NamedTextColor.DARK_GRAY));
                i++; j++;
            } else if (j < newLines.length && (i == oldLines.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
                lines.add(diffLine("+ ", NamedTextColor.GREEN, newLines[j], null));
                j++;
            } else if (i < oldLines.length) {
                lines.add(diffLine("- ", NamedTextColor.RED, oldLines[i], null));
                i++;
            }
        }
        return joinLimited(lines, 80);
    }

    private static Component diffLine(String prefix, NamedTextColor prefixColor, String code, NamedTextColor fallbackColor) {
        Component highlighted = highlightGuess(code);
        if (fallbackColor != null) highlighted = highlighted.colorIfAbsent(fallbackColor);
        return Component.text(prefix, prefixColor).append(highlighted);
    }

    private static Component highlightGuess(String code) {
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.startsWith("\"") || trimmed.startsWith("{") || trimmed.startsWith("}") || trimmed.startsWith("[") || trimmed.startsWith("]")) {
            try {
                return json(code);
            } catch (Exception ignored) {
                return Component.text(code);
            }
        }
        try {
            return javascript(code);
        } catch (Exception ignored) {
            return Component.text(code);
        }
    }

    private static Component joinLimited(List<Component> lines, int maxLines) {
        if (lines.size() > maxLines) {
            int keepHead = maxLines / 2;
            int keepTail = maxLines - keepHead - 1;
            List<Component> limited = new ArrayList<>();
            limited.addAll(lines.subList(0, keepHead));
            limited.add(Component.text("... " + (lines.size() - keepHead - keepTail) + " diff lines hidden ...", NamedTextColor.GRAY));
            limited.addAll(lines.subList(lines.size() - keepTail, lines.size()));
            lines = limited;
        }
        Component component = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) component = component.append(Component.newline());
            component = component.append(lines.get(i));
        }
        return component;
    }

    public static Component highlight(final Language language, final String code) {
        if (code == null || code.isEmpty()) {
            return Component.empty();
        }

        try (var parser = new TSParser();) {

            // set the language to be javascript
            parser.setLanguage(language.language());

            var tree = parser.parseString(null, code);
            var root = tree.getRootNode();
            var query = language.query();


            try (
                    var cursor = new TSQueryCursor();
                    ) {

                cursor.exec(query, root);

                var match = new TSQueryMatch();

                // first step: extract the highlighted words
                var highlightedWords = new LinkedList<Highlight>();

                while (cursor.nextCapture(match)) {
                    var captures = match.getCaptures();

                    for (var capture : captures) {
                        int start = capture.getNode().getStartByte();
                        int end = capture.getNode().getEndByte();

                        var captureName = query.getCaptureNameForId(capture.getIndex());
                        highlightedWords.push(new Highlight(code.substring(start, end), captureName, start, end, getTextColor(captureName)));

                    }
                }

                highlightedWords.sort(Comparator.comparingInt(Highlight::start));
                return toComponent(code, highlightedWords);
            }
        }
    }

    private static @NonNull TextColor getTextColor(String captureName) {
        return DEFAULT_THEME.colorFor(captureName);
    }


    private static Component toComponent(final String sourceCode, final LinkedList<Highlight> highlighted) {
        if (highlighted.isEmpty()) {
            return Component.text(sourceCode);
        }

        Component highlightedCode = Component.empty();


        int cursor = 0;
        // then rebuild the string w/ highlights
        for (int i = 0; i < highlighted.size(); i++) {
            var highlight = highlighted.get(i);
            var priorHighlight = i == 0 ? ZERO : highlighted.get(i - 1);

            if (highlight.start() < cursor) {
                continue;
            }

            // the code prior to the highlight
            var before = sourceCode.substring(priorHighlight.end(), highlight.start());

            // append any unhighlighted code
            highlightedCode =
                    // unhighlighted code
                    highlightedCode.append(Component.text(before))
                            // highlighted code
                            .append(
                                    Component.text(sourceCode.substring(highlight.start(), highlight.end())).color(highlight.color)
                            );

            cursor = highlight.end();
        }

        return highlightedCode.append(Component.text(sourceCode.substring(highlighted.getLast().end())));
    }
}
