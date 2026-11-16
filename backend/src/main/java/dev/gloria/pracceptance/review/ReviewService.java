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
 * Reviews a diff against a set of acceptance criteria.
 *
 * <p>The order matters: the diff is parsed first (if it cannot be, no call to the model is
 * spent), then the model is asked, and finally every citation is checked against the diff.
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
                // Citations are checked against THIS request's diff, never another one.
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
     * A criterion the model said nothing usable about.
     *
     * <p>It comes back as Not verified, which is exactly what it is: nobody checked it. What must
     * not happen is for it to vanish from the list, or to drag the other criteria down with it.
     */
    private static CriterionResult notEvaluated(String criterion, String problem) {
        String note = problem == null
                ? "The model did not evaluate this criterion: it returned no verdict for it."
                : "The model answered this criterion, but " + problem;
        return new CriterionResult(criterion, ReviewStatus.NOT_VERIFIED, null, null, note);
    }
}
