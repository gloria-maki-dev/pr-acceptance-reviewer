package dev.gloria.pracceptance.review;

import java.util.List;

/**
 * A complete review: one result per criterion, which model ran it, and who actually answered.
 *
 * @param model the configured model identifier
 * @param provider who answered: the Anthropic API, or whatever stands in for it
 * @param officialProvider {@code false} when the answer did not come from the Anthropic API
 */
public record ReviewResult(
        List<CriterionResult> results, String model, String provider, boolean officialProvider) {

    public ReviewResult {
        results = List.copyOf(results);
    }
}
