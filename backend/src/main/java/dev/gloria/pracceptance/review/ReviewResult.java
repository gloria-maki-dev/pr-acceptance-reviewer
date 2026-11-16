package dev.gloria.pracceptance.review;

import java.util.List;

/**
 * La revisión completa de un diff: un resultado por criterio, qué modelo la hizo y quién atendió
 * de verdad la petición.
 *
 * @param model identificador del modelo configurado
 * @param provider quién respondió: la API de Claude, o lo que haya en su lugar
 * @param officialProvider {@code false} si la respuesta no vino de la API de Claude
 */
public record ReviewResult(
        List<CriterionResult> results, String model, String provider, boolean officialProvider) {

    public ReviewResult {
        results = List.copyOf(results);
    }
}
