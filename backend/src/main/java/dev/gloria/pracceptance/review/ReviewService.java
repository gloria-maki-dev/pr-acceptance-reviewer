package dev.gloria.pracceptance.review;

import dev.gloria.pracceptance.diff.DiffParser;
import dev.gloria.pracceptance.diff.ParsedDiff;
import dev.gloria.pracceptance.model.ModelClient;
import dev.gloria.pracceptance.model.ModelResponseParser;
import dev.gloria.pracceptance.model.ParsedModelResponse;
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

        List<String> ids = PromptBuilder.criterionIds(criteria.size());
        ParsedModelResponse parsed = responseParser.parse(response, ids);

        List<CriterionResult> results = new ArrayList<>(criteria.size());
        for (int i = 0; i < criteria.size(); i++) {
            String id = ids.get(i);
            ModelVerdict verdict = parsed.verdicts().get(id);
            if (verdict != null) {
                // Las citas se comprueban contra el diff de ESTA petición, nunca contra otro.
                results.add(verifier.verify(criteria.get(i), verdict, diff));
            } else {
                results.add(notEvaluated(criteria.get(i), parsed.problems().get(id)));
            }
        }
        return new ReviewResult(
                results,
                modelClient.modelName(),
                modelClient.providerDescription(),
                modelClient.isOfficialProvider());
    }

    /**
     * Un criterio del que el modelo no dijo nada aprovechable.
     *
     * <p>Sale como No verificado, que es exactamente lo que es: nadie lo ha comprobado. Lo que no
     * puede pasar es que se pierda de la lista, ni que arrastre consigo los demás criterios.
     */
    private static CriterionResult notEvaluated(String criterion, String problem) {
        String note = problem == null
                ? "El modelo no evaluó este criterio: no devolvió ningún veredicto para él."
                : "El modelo respondió a este criterio, pero " + problem;
        return new CriterionResult(criterion, ReviewStatus.UNVERIFIED, null, null, note);
    }
}
