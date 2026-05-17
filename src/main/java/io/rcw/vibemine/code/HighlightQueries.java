package io.rcw.vibemine.code;

public interface HighlightQueries {
     String JAVASCRIPT_HIGHLIGHT_QUERY = """
                (string) @string
                (template_string) @string
                (escape_sequence) @string.escape
                (number) @number
                
                (function_declaration
                  name: (identifier) @function)
                
                (method_definition
                  name: (property_identifier) @method)
               
                
                
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
                """;

     String JSON_HIGHLIGHT_QUERY = """
             (string) @string
             (number) @number
             [
               (null)
               (true)
               (false)
             ] @constant.builtin
             ; for now don't capture escape or comments
             ;(escape_sequence) @escape
             ;(comment) @comment
     """;

}
