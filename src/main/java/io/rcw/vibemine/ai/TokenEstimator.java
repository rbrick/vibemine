package io.rcw.vibemine.ai;

/**
 * Lightweight token estimator for UI feedback. This is not provider-accurate;
 * it uses a common English/code heuristic of roughly four characters per token.
 */
public final class TokenEstimator {
    private TokenEstimator() {}

    public static int estimate(String text) {
        if (text == null || text.isBlank()) return 0;

        int nonWhitespace = 0;
        int whitespaceRuns = 0;
        boolean inWhitespace = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!inWhitespace) whitespaceRuns++;
                inWhitespace = true;
            } else {
                nonWhitespace++;
                inWhitespace = false;
            }
        }

        int characterEstimate = (int) Math.ceil(text.length() / 4.0D);
        int wordLikeEstimate = (int) Math.ceil((nonWhitespace + whitespaceRuns) / 4.0D);
        return Math.max(1, Math.max(characterEstimate, wordLikeEstimate));
    }

    public static String compact(int tokens) {
        if (tokens >= 1_000_000) return String.format("%.1fm", tokens / 1_000_000.0D);
        if (tokens >= 1_000) return String.format("%.1fk", tokens / 1_000.0D);
        return Integer.toString(tokens);
    }
}
