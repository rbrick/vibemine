package io.rcw.vibemine.code;

import org.treesitter.TSLanguage;
import org.treesitter.TSQuery;
import org.treesitter.TreeSitterJavascript;
import org.treesitter.TreeSitterJson;

import java.util.function.Supplier;

public enum Language {
    JAVASCRIPT(TreeSitterJavascript::new, HighlightQueries.JAVASCRIPT_HIGHLIGHT_QUERY),
    JSON(TreeSitterJson::new, HighlightQueries.JSON_HIGHLIGHT_QUERY);


    private final TSLanguage language;
    private final String query;

    Language(final Supplier<TSLanguage> languageSupplier, final String query) {
        this.query = query;
        this.language = languageSupplier.get();
    }

    public TSQuery query() {
        return new TSQuery(language, query);
    }

    public TSLanguage language() {
        return this.language;
    }
}
