package io.rcw.vibemine.code;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.treesitter.*;

import java.util.LinkedList;

public class Highlighting {
    record Highlight(int start, int end, TextColor color) { }

    public static Component highlight(final String code) {
        try (var parser = new TSParser();
             var language = new TreeSitterJavascript()) {

            // set the language to be javascript
            parser.setLanguage(language);

            var tree = parser.parseString(null, code);
            var root = tree.getRootNode();
            var query = query(language);

            try (
                    var cursor = new TSQueryCursor();
                    ) {
                var match = new TSQueryMatch();

                // first step: extract the highlighted words
                var highlightedWords = new LinkedList<Highlight>();

                while (cursor.nextCapture(match)) {
                    var captures = match.getCaptures();

                    for (var capture : captures) {
                        int start = capture.getNode().getStartByte();
                        int end = capture.getNode().getEndByte();

                        // TODO(ryan) do something with capture name
                        var captureName = query.getCaptureNameForId(capture.getIndex());

                        switch (captureName) {
                            case "keyword":
                            case "constant.builtin":
                                highlightedWords.push(new Highlight(start, end, TextColor.color(0xffb86c)));
                            case "string":
                                highlightedWords.push(new Highlight(start, end, TextColor.color(0x50fa7b)));
                            case "number":
                                highlightedWords.push(new Highlight(start, end, TextColor.color(0x8be9fd)));
                        }
                    }
                }
                return toComponent(code, highlightedWords);
            }
        }
    }


    private static Component toComponent(final String sourceCode, final LinkedList<Highlight> highlighted) {
        Component highlightedCode = Component.empty();

        // then rebuild the string w/ highlights
        for (int i = 0; i < highlighted.size(); i++) {
            var highlight = highlighted.get(i);
            var priorHighlight = i == 0 ? new Highlight(0, 0, TextColor.color(0x0)) : highlighted.get(i - 1);
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
        }

        return highlightedCode.append(Component.text(sourceCode.substring(highlighted.getLast().end())));
    }

    private static TSQuery query(TSLanguage language) {
        return new TSQuery(language, """
                (string) @string
                (template_string) @string
                (escape_sequence) @string.escape
                
                (number) @number
                
                [
                (true)
                (false)
                (null)
                (undefined)
                ] @constant.builtin
                
                [
                  "as"
                  "async"
                  "await"
                  "break"
                  "case"
                  "catch"
                  "class"
                  "const"
                  "continue"
                  "debugger"
                  "default"
                  "delete"
                  "do"
                  "else"
                  "export"
                  "extends"
                  "finally"
                  "for"
                  "from"
                  "function"
                  "get"
                  "if"
                  "import"
                  "in"
                  "instanceof"
                  "let"
                  "new"
                  "of"
                  "return"
                  "set"
                  "static"
                  "switch"
                  "target"
                  "throw"
                  "try"
                  "typeof"
                  "var"
                  "void"
                  "while"
                  "with"
                  "yield"
                ] @keyword
                """);
    }

}
