package dev.gloria.pracceptance.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.review.Evidence;
import dev.gloria.pracceptance.review.ReviewStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Turns the model's raw answer into verdicts, saying what came back wrong.
 *
 * <p>It assumes nothing: a model can return prose, truncated JSON, a status that does not exist,
 * or skip criteria. What parses is kept; what does not is recorded per criterion so the screen
 * can say "Not verified" and explain why, instead of throwing the whole review away. Only when
 * no verdict is left standing is {@link ModelResponseException} raised.
 */
@Component
public class ModelResponseParser {

    private final ObjectMapper objectMapper;

    public ModelResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param criterionIds the identifiers the model was asked for, in order
     * @throws ModelResponseException only when nothing in the answer can be used
     */
    public ParsedModelResponse parse(String rawResponse, List<String> criterionIds) {
        JsonNode root = readJson(rawResponse);

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            throw new ModelResponseException("The model's JSON has no `results` list.");
        }

        Set<String> expected = new LinkedHashSet<>(criterionIds);
        Map<String, ModelVerdict> verdicts = new LinkedHashMap<>();
        Map<String, String> problems = new LinkedHashMap<>();
        int ignored = 0;

        for (JsonNode node : results) {
            String id = readCriterionId(node);
            if (id == null || !expected.contains(id)) {
                // A verdict with no known criterion cannot be placed on any of them.
                ignored++;
                continue;
            }
            if (verdicts.containsKey(id) || problems.containsKey(id)) {
                verdicts.remove(id);
                problems.put(id, "the model returned more than one verdict for it and there is no telling which counts");
                continue;
            }
            try {
                verdicts.put(id, new ModelVerdict(readStatus(node), readExplanation(node), readEvidence(node)));
            } catch (ModelResponseException e) {
                problems.put(id, lowerFirst(e.getMessage()));
            }
        }

        if (verdicts.isEmpty()) {
            throw new ModelResponseException(
                    "The model returned no usable verdict: " + results.size() + " in the answer, "
                            + problems.size() + " malformed and " + ignored
                            + " without a recognisable criterion identifier.");
        }
        return new ParsedModelResponse(verdicts, problems);
    }

    private static String readCriterionId(JsonNode node) {
        JsonNode id = node.get("criterionId");
        if (id != null && id.isTextual() && !id.textValue().isBlank()) {
            return id.textValue().strip().toUpperCase();
        }
        // Tolerance: a model may return the number instead of the identifier.
        JsonNode index = node.get("criterionIndex");
        if (index != null && index.isInt()) {
            return "C" + index.intValue();
        }
        return null;
    }

    private static String lowerFirst(String message) {
        if (message == null || message.isEmpty()) {
            return "it came back malformed";
        }
        return Character.toLowerCase(message.charAt(0)) + message.substring(1);
    }

    private JsonNode readJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new ModelResponseException("The model returned nothing.");
        }

        String text = rawResponse.strip();
        if (text.indexOf('{') < 0) {
            throw new ModelResponseException("The model returned no JSON: there is no object in the answer.");
        }

        List<JsonNode> answers = new ArrayList<>();
        JsonProcessingException lastFailure = null;

        // A JSON object may arrive wrapped in prose, and the prose may carry braces of its own
        // ("the {results} object I return: {...}"). We try every opening brace and keep the
        // objects that really are an answer.
        for (int start = text.indexOf('{'); start >= 0; start = text.indexOf('{', start + 1)) {
            int end = findMatchingBrace(text, start);
            if (end < 0) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
                if (node != null && node.isObject() && node.has("results")) {
                    answers.add(node);
                    start = end;
                }
            } catch (JsonProcessingException e) {
                lastFailure = e;
            }
        }

        if (answers.size() > 1) {
            throw new ModelResponseException(
                    "The model returned " + answers.size() + " different answers and there is no telling which counts.");
        }
        if (answers.size() == 1) {
            return answers.get(0);
        }
        if (lastFailure != null) {
            throw new ModelResponseException(
                    "The model did not return valid JSON: " + lastFailure.getOriginalMessage(), lastFailure);
        }
        throw new ModelResponseException("The model did not return valid JSON: the answer is incomplete.");
    }

    /**
     * Returns the position of the brace closing the one that opens at {@code start}, skipping
     * braces that live inside a string. Returns -1 if it never closes.
     */
    private static int findMatchingBrace(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static ReviewStatus readStatus(JsonNode node) {
        String raw = text(node, "status");
        if (raw == null) {
            throw new ModelResponseException("A verdict has no `status`.");
        }
        try {
            return ReviewStatus.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ModelResponseException(
                    "`" + raw + "` is not a valid status. The statuses are MET, NOT_MET and NOT_VERIFIED.", e);
        }
    }

    private static String readExplanation(JsonNode node) {
        String explanation = text(node, "explanation");
        if (explanation == null || explanation.isBlank()) {
            throw new ModelResponseException("A verdict arrives with no explanation.");
        }
        return explanation.strip();
    }

    private static Evidence readEvidence(JsonNode node) {
        JsonNode evidence = node.get("evidence");
        if (evidence == null || evidence.isNull()) {
            return null;
        }
        if (!evidence.isObject()) {
            throw new ModelResponseException("`evidence` must be an object or null.");
        }

        String file = text(evidence, "file");
        if (file == null || file.isBlank()) {
            throw new ModelResponseException("A citation arrives with no `file`.");
        }

        String rawSide = text(evidence, "side");
        if (rawSide == null) {
            throw new ModelResponseException("A citation arrives with no `side`.");
        }
        DiffSide side;
        try {
            side = DiffSide.valueOf(rawSide.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ModelResponseException(
                    "`" + rawSide + "` is not a side of the diff. The sides are ADDED, REMOVED and CONTEXT.", e);
        }

        JsonNode lineNumber = evidence.get("lineNumber");
        if (lineNumber == null || !lineNumber.isInt()) {
            throw new ModelResponseException("A citation arrives with no integer `lineNumber`.");
        }

        String snippet = text(evidence, "snippet");
        if (snippet == null || snippet.isBlank()) {
            throw new ModelResponseException("A citation arrives with no `snippet`.");
        }

        return new Evidence(file.strip(), side, lineNumber.intValue(), snippet);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? null : value.textValue();
    }
}
