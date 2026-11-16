package dev.gloria.pracceptance.review;

import dev.gloria.pracceptance.diff.DiffParser;
import dev.gloria.pracceptance.diff.ParsedDiff;
import dev.gloria.pracceptance.model.ModelClient;
import dev.gloria.pracceptance.model.ModelResponseParser;
import dev.gloria.pracceptance.model.ModelVerdict;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Revisa un diff contra unos criterios de aceptación.
 *
 * <p>El orden importa: primero se interpreta el diff (si no se puede, no se gasta una llamada al
 * modelo), después se le pregunta al modelo, y al final se comprueba cada cita contra el diff.
 */
@Service
public class ReviewService {

    private final ModelClient modelClient;
    private final ModelResponseParser responseParser;
    private final DiffParser diffParser;
    private final EvidenceVerifier verifier;
    private final PromptBuilder promptBuilder;

    public ReviewService(
            ModelClient modelClient,
            ModelResponseParser responseParser,
            DiffParser diffParser,
            EvidenceVerifier verifier) {
        this(modelClient, responseParser, diffParser, verifier, new PromptBuilder());
    }

    @Autowired
    public ReviewService(
            ModelClient modelClient,
            ModelResponseParser responseParser,
            DiffParser diffParser,
            EvidenceVerifier verifier,
            PromptBuilder promptBuilder) {
        this.modelClient = modelClient;
        this.responseParser = responseParser;
        this.diffParser = diffParser;
        this.verifier = verifier;
        this.promptBuilder = promptBuilder;
    }

    public ReviewResult review(List<String> criteria, String rawDiff) {
        ParsedDiff diff = diffParser.parse(rawDiff);

        String response = modelClient.completeJson(
                promptBuilder.systemPrompt(), promptBuilder.userPrompt(criteria, diff));

        List<ModelVerdict> verdicts = responseParser.parse(response, criteria.size());

        List<CriterionResult> results = new ArrayList<>(criteria.size());
        for (int i = 0; i < criteria.size(); i++) {
            results.add(verifier.verify(criteria.get(i), verdicts.get(i), diff));
        }
        return new ReviewResult(results, modelClient.modelName());
    }
}
