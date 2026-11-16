package dev.gloria.pracceptance.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gloria.pracceptance.diff.DiffSide;
import dev.gloria.pracceptance.review.ReviewStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelResponseParserTest {

    private final ModelResponseParser parser = new ModelResponseParser(new ObjectMapper());

    private static final List<String> TWO = List.of("C1", "C2");
    private static final List<String> ONE = List.of("C1");

    private static final String VALID =
            """
            {
              "results": [
                {
                  "criterionId": "C1",
                  "status": "MET",
                  "explanation": "El resumen del carrito pasa a decir «Estimated total».",
                  "evidence": {
                    "file": "src/components/CartSummary.tsx",
                    "side": "ADDED",
                    "lineNumber": 20,
                    "snippet": "<span className=\\"cart-summary__label\\">Estimated total</span>"
                  }
                },
                {
                  "criterionId": "C2",
                  "status": "NOT_MET",
                  "explanation": "Order history changes as well.",
                  "evidence": {
                    "file": "src/components/OrderHistory.tsx",
                    "side": "ADDED",
                    "lineNumber": 32,
                    "snippet": "Estimated total:"
                  }
                }
              ]
            }
            """;

    @Test
    @DisplayName("turns a valid answer into one verdict per identifier")
    void validAnswer() {
        ParsedModelResponse parsed = parser.parse(VALID, TWO);

        assertThat(parsed.problems()).isEmpty();
        assertThat(parsed.verdicts()).containsOnlyKeys("C1", "C2");
        assertThat(parsed.verdicts().get("C1").status()).isEqualTo(ReviewStatus.MET);
        assertThat(parsed.verdicts().get("C1").evidence().side()).isEqualTo(DiffSide.ADDED);
        assertThat(parsed.verdicts().get("C1").evidence().lineNumber()).isEqualTo(20);
        assertThat(parsed.verdicts().get("C2").status()).isEqualTo(ReviewStatus.NOT_MET);
    }

    @Test
    @DisplayName("the order of the answer does not matter: each verdict goes to its identifier")
    void orderDoesNotMatter() {
        String json =
                """
                {"results":[
                  {"criterionId":"C2","status":"NOT_VERIFIED","explanation":"Second","evidence":null},
                  {"criterionId":"C1","status":"MET","explanation":"First","evidence":null}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, TWO);

        assertThat(parsed.verdicts().get("C1").explanation()).isEqualTo("First");
        assertThat(parsed.verdicts().get("C2").explanation()).isEqualTo("Second");
    }

    @Test
    @DisplayName("an answer missing a criterion keeps the others")
    void partialAnswer() {
        String json = """
                {"results":[{"criterionId":"C1","status":"MET","explanation":"Yes."}]}
                """;

        ParsedModelResponse parsed = parser.parse(json, TWO);

        assertThat(parsed.verdicts()).containsOnlyKeys("C1");
        assertThat(parsed.problems()).isEmpty();
    }

    @Test
    @DisplayName("a malformed verdict does not drag the others down, and the reason is recorded")
    void oneMalformedVerdict() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Yes.",
                   "evidence":{"file":"a.tsx","side":"ADDED","snippet":"x"}},
                  {"criterionId":"C2","status":"NOT_MET","explanation":"No."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, TWO);

        assertThat(parsed.verdicts()).containsOnlyKeys("C2");
        assertThat(parsed.problems().get("C1")).contains("lineNumber");
    }

    @Test
    @DisplayName("two verdicts for the same criterion leave that criterion without one")
    void repeatedIdentifier() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Yes."},
                  {"criterionId":"C1","status":"NOT_MET","explanation":"Pues no."},
                  {"criterionId":"C2","status":"MET","explanation":"This one yes."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, TWO);

        assertThat(parsed.verdicts()).containsOnlyKeys("C2");
        assertThat(parsed.problems().get("C1")).contains("more than one verdict");
    }

    @Test
    @DisplayName("a verdict with an identifier that was not asked for is ignored")
    void inventedIdentifier() {
        String json =
                """
                {"results":[
                  {"criterionId":"C9","status":"MET","explanation":"From another ticket."},
                  {"criterionId":"C1","status":"MET","explanation":"From this one."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, ONE);

        assertThat(parsed.verdicts()).containsOnlyKeys("C1");
        assertThat(parsed.verdicts().get("C1").explanation()).isEqualTo("From this one.");
    }

    @Test
    @DisplayName("tolerates the model returning the number instead of the identifier")
    void numberInsteadOfIdentifier() {
        String json = """
                {"results":[{"criterionIndex":2,"status":"MET","explanation":"Yes."}]}
                """;

        assertThat(parser.parse(json, TWO).verdicts()).containsOnlyKeys("C2");
    }

    @Test
    @DisplayName("accepts a verdict with no evidence")
    void withoutEvidence() {
        String json = """
                {"results":[{"criterionId":"C1","status":"NOT_VERIFIED","explanation":"The diff does not show it."}]}
                """;

        assertThat(parser.parse(json, ONE).verdicts().get("C1").evidence()).isNull();
    }

    @Test
    @DisplayName("extracts the JSON even when it arrives in a code fence")
    void fencedJson() {
        String json = "```json\n{\"results\":[{\"criterionId\":\"C1\",\"status\":\"MET\",\"explanation\":\"Sí.\"}]}\n```";

        assertThat(parser.parse(json, ONE).verdicts()).hasSize(1);
    }

    @Test
    @DisplayName("accepts valid JSON preceded by prose containing braces")
    void proseWithBracesInFront() {
        String json = "The {results} object I return:\n"
                + "{\"results\":[{\"criterionId\":\"C1\",\"status\":\"MET\",\"explanation\":\"Sí.\"}]}";

        assertThat(parser.parse(json, ONE).verdicts()).hasSize(1);
    }

    @Test
    @DisplayName("rejects an answer that is not JSON")
    void notJson() {
        assertThatThrownBy(() -> parser.parse("Sorry, I cannot review this diff.", ONE))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    @DisplayName("rejects truncated JSON")
    void truncatedJson() {
        assertThatThrownBy(() -> parser.parse("{\"results\":[{\"criterionId\":\"C1\",", ONE))
                .isInstanceOf(ModelResponseException.class);
    }

    @Test
    @DisplayName("rejects an answer with two objects instead of taking the first")
    void twoAnswers() {
        String json = "{\"results\":[{\"criterionId\":\"C1\",\"status\":\"MET\",\"explanation\":\"Borrador.\"}]}"
                + " {\"results\":[{\"criterionId\":\"C1\",\"status\":\"NOT_MET\",\"explanation\":\"Corregido.\"}]}";

        assertThatThrownBy(() -> parser.parse(json, ONE))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("2 different answers");
    }

    @Test
    @DisplayName("fails only when no verdict is left standing")
    void noUsableVerdict() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"MAYBE","explanation":"Who knows."},
                  {"criterionId":"C2","status":"MET","explanation":"   "}
                ]}
                """;

        assertThatThrownBy(() -> parser.parse(json, TWO))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("no usable verdict");
    }

    @Test
    @DisplayName("an unknown status only sinks its own criterion, and says which it was")
    void unknownStatus() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"MAYBE","explanation":"Who knows."},
                  {"criterionId":"C2","status":"MET","explanation":"This one yes."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, TWO);

        assertThat(parsed.verdicts()).containsOnlyKeys("C2");
        assertThat(parsed.problems().get("C1")).contains("MAYBE");
    }

    @Test
    @DisplayName("an unknown diff side only sinks its own criterion")
    void unknownSide() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Yes.",
                   "evidence":{"file":"a.tsx","side":"UPWARDS","lineNumber":3,"snippet":"x"}},
                  {"criterionId":"C2","status":"MET","explanation":"This one yes."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, TWO);

        assertThat(parsed.problems().get("C1")).contains("UPWARDS");
        assertThat(parsed.verdicts()).containsOnlyKeys("C2");
    }

    @Test
    @DisplayName("rejects an answer with no `results` list")
    void withoutResults() {
        assertThatThrownBy(() -> parser.parse("{\"verdicts\":[]}", ONE))
                .isInstanceOf(ModelResponseException.class);
    }
}
