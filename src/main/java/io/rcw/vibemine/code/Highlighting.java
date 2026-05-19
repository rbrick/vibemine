package io.rcw.vibemine.code;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.NonNull;
import org.treesitter.*;

import java.util.Comparator;
import java.util.LinkedList;
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


    public static Component highlight(final Language language, final String code) {
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
        highlighted.forEach(highlight -> System.out.printf("%s [%s] (%d->%d)%n", highlight.text, highlight.kind, highlight.start, highlight.end));

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
