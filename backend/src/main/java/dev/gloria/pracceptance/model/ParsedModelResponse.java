package dev.gloria.pracceptance.model;

import java.util.Map;

/**
 * Whatever could be salvaged from the model's answer.
 *
 * <p>A half-answer does not invalidate the whole review. The verdicts that parse are kept; for
 * the ones that do not, the reason is recorded so the reviewer can be told what happened to that
 * particular criterion. A criterion in neither map is one the model simply did not evaluate.
 *
 * @param verdicts usable verdicts, by criterion identifier
 * @param problems why a criterion's verdict could not be used, by criterion identifier
 */
public record ParsedModelResponse(Map<String, ModelVerdict> verdicts, Map<String, String> problems) {

    public ParsedModelResponse {
        verdicts = Map.copyOf(verdicts);
        problems = Map.copyOf(problems);
    }
}
