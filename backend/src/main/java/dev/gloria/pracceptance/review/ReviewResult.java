package dev.gloria.pracceptance.review;

import java.util.List;

/** La revisión completa de un diff: un resultado por criterio, y qué modelo la hizo. */
public record ReviewResult(List<CriterionResult> results, String model) {

    public ReviewResult {
        results = List.copyOf(results);
    }
}
