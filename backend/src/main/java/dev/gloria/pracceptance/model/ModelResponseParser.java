package dev.gloria.pracceptance.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.review.Evidence;
import dev.gloria.pracceptance.review.ReviewStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Convierte la respuesta en bruto del modelo en veredictos, o falla diciendo qué venía mal.
 *
 * <p>No da nada por hecho: un modelo puede devolver prosa, JSON truncado, un estado que no existe
 * o menos veredictos que criterios. Todo eso es {@link ModelResponseException}, nunca un resultado
 * a medias que luego se muestre como si fuera bueno.
 */
@Component
public class ModelResponseParser {

    private final ObjectMapper objectMapper;

    public ModelResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ModelVerdict> parse(String rawResponse, int expectedCriteria) {
        JsonNode root = readJson(rawResponse);

        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            throw new ModelResponseException("El JSON del modelo no trae la lista «results».");
        }
        if (results.size() != expectedCriteria) {
            throw new ModelResponseException("El modelo devolvió " + results.size()
                    + " veredictos y se esperaban " + expectedCriteria + ", uno por criterio.");
        }

        ModelVerdict[] ordered = new ModelVerdict[expectedCriteria];
        for (JsonNode node : results) {
            int index = readCriterionIndex(node, expectedCriteria);
            if (ordered[index - 1] != null) {
                throw new ModelResponseException(
                        "El modelo devolvió dos veredictos para el criterio " + index + ".");
            }
            ordered[index - 1] = new ModelVerdict(readStatus(node), readExplanation(node), readEvidence(node));
        }

        List<ModelVerdict> verdicts = new ArrayList<>(expectedCriteria);
        for (int i = 0; i < expectedCriteria; i++) {
            if (ordered[i] == null) {
                throw new ModelResponseException("Falta el veredicto del criterio " + (i + 1) + ".");
            }
            verdicts.add(ordered[i]);
        }
        return List.copyOf(verdicts);
    }

    private JsonNode readJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new ModelResponseException("El modelo no devolvió nada.");
        }

        String text = rawResponse.strip();
        if (text.indexOf('{') < 0) {
            throw new ModelResponseException("El modelo no devolvió JSON: no hay ningún objeto en la respuesta.");
        }

        List<JsonNode> answers = new ArrayList<>();
        JsonProcessingException lastFailure = null;

        // Un objeto JSON puede venir envuelto en prosa, y la prosa puede traer sus propias llaves
        // («el objeto {results} que devuelvo: {...}»). Probamos cada apertura de llave y nos
        // quedamos con los objetos que de verdad son una respuesta.
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
                    "El modelo devolvió " + answers.size() + " respuestas distintas y no se sabe cuál vale.");
        }
        if (answers.size() == 1) {
            return answers.get(0);
        }
        if (lastFailure != null) {
            throw new ModelResponseException(
                    "El modelo no devolvió JSON válido: " + lastFailure.getOriginalMessage(), lastFailure);
        }
        throw new ModelResponseException("El modelo no devolvió JSON válido: la respuesta está incompleta.");
    }

    /**
     * Devuelve la posición de la llave que cierra la que abre en {@code start}, saltándose las
     * llaves que vivan dentro de una cadena. Devuelve -1 si nunca se cierra.
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

    private static int readCriterionIndex(JsonNode node, int expectedCriteria) {
        JsonNode index = node.get("criterionIndex");
        if (index == null || !index.isInt()) {
            throw new ModelResponseException("Un veredicto no trae «criterionIndex» como número entero.");
        }
        int value = index.intValue();
        if (value < 1 || value > expectedCriteria) {
            throw new ModelResponseException("El criterio " + value + " no existe: hay " + expectedCriteria + ".");
        }
        return value;
    }

    private static ReviewStatus readStatus(JsonNode node) {
        String raw = text(node, "status");
        if (raw == null) {
            throw new ModelResponseException("Un veredicto no trae «status».");
        }
        try {
            return ReviewStatus.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ModelResponseException(
                    "«" + raw + "» no es un estado válido. Los estados son MET, NOT_MET y UNVERIFIED.", e);
        }
    }

    private static String readExplanation(JsonNode node) {
        String explanation = text(node, "explanation");
        if (explanation == null || explanation.isBlank()) {
            throw new ModelResponseException("Un veredicto llega sin explicación.");
        }
        return explanation.strip();
    }

    private static Evidence readEvidence(JsonNode node) {
        JsonNode evidence = node.get("evidence");
        if (evidence == null || evidence.isNull()) {
            return null;
        }
        if (!evidence.isObject()) {
            throw new ModelResponseException("«evidence» tiene que ser un objeto o null.");
        }

        String file = text(evidence, "file");
        if (file == null || file.isBlank()) {
            throw new ModelResponseException("Una cita llega sin «file».");
        }

        String rawSide = text(evidence, "side");
        if (rawSide == null) {
            throw new ModelResponseException("Una cita llega sin «side».");
        }
        DiffSide side;
        try {
            side = DiffSide.valueOf(rawSide.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ModelResponseException(
                    "«" + rawSide + "» no es un lado del diff. Los lados son ADDED, REMOVED y CONTEXT.", e);
        }

        JsonNode lineNumber = evidence.get("lineNumber");
        if (lineNumber == null || !lineNumber.isInt()) {
            throw new ModelResponseException("Una cita llega sin «lineNumber» como número entero.");
        }

        String snippet = text(evidence, "snippet");
        if (snippet == null || snippet.isBlank()) {
            throw new ModelResponseException("Una cita llega sin «snippet».");
        }

        return new Evidence(file.strip(), side, lineNumber.intValue(), snippet);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? null : value.textValue();
    }
}
