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

    private static final List<String> DOS = List.of("C1", "C2");
    private static final List<String> UNO = List.of("C1");

    private static final String VALIDA =
            """
            {
              "results": [
                {
                  "criterionId": "C1",
                  "status": "MET",
                  "explanation": "El resumen del carrito pasa a decir «Total estimado».",
                  "evidence": {
                    "file": "src/components/CartSummary.tsx",
                    "side": "ADDED",
                    "lineNumber": 20,
                    "snippet": "<span className=\\"cart-summary__label\\">Total estimado</span>"
                  }
                },
                {
                  "criterionId": "C2",
                  "status": "NOT_MET",
                  "explanation": "El historial de pedidos también cambia.",
                  "evidence": {
                    "file": "src/components/OrderHistory.tsx",
                    "side": "ADDED",
                    "lineNumber": 32,
                    "snippet": "Total estimado:"
                  }
                }
              ]
            }
            """;

    @Test
    @DisplayName("convierte una respuesta válida en un veredicto por identificador")
    void respuestaValida() {
        ParsedModelResponse parsed = parser.parse(VALIDA, DOS);

        assertThat(parsed.problems()).isEmpty();
        assertThat(parsed.verdicts()).containsOnlyKeys("C1", "C2");
        assertThat(parsed.verdicts().get("C1").status()).isEqualTo(ReviewStatus.MET);
        assertThat(parsed.verdicts().get("C1").evidence().side()).isEqualTo(DiffSide.ADDED);
        assertThat(parsed.verdicts().get("C1").evidence().lineNumber()).isEqualTo(20);
        assertThat(parsed.verdicts().get("C2").status()).isEqualTo(ReviewStatus.NOT_MET);
    }

    @Test
    @DisplayName("el orden en que venga la respuesta da igual: cada veredicto va a su identificador")
    void elOrdenDaIgual() {
        String json =
                """
                {"results":[
                  {"criterionId":"C2","status":"UNVERIFIED","explanation":"Segundo","evidence":null},
                  {"criterionId":"C1","status":"MET","explanation":"Primero","evidence":null}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, DOS);

        assertThat(parsed.verdicts().get("C1").explanation()).isEqualTo("Primero");
        assertThat(parsed.verdicts().get("C2").explanation()).isEqualTo("Segundo");
    }

    @Test
    @DisplayName("una respuesta a la que le falta un criterio conserva los demás")
    void respuestaParcial() {
        String json = """
                {"results":[{"criterionId":"C1","status":"MET","explanation":"Sí."}]}
                """;

        ParsedModelResponse parsed = parser.parse(json, DOS);

        assertThat(parsed.verdicts()).containsOnlyKeys("C1");
        assertThat(parsed.problems()).isEmpty();
    }

    @Test
    @DisplayName("un veredicto mal formado no arrastra a los demás, y se anota por qué")
    void unVeredictoMalFormado() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Sí.",
                   "evidence":{"file":"a.tsx","side":"ADDED","snippet":"x"}},
                  {"criterionId":"C2","status":"NOT_MET","explanation":"No."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, DOS);

        assertThat(parsed.verdicts()).containsOnlyKeys("C2");
        assertThat(parsed.problems().get("C1")).contains("lineNumber");
    }

    @Test
    @DisplayName("dos veredictos para el mismo criterio dejan ese criterio sin veredicto")
    void identificadorRepetido() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Sí."},
                  {"criterionId":"C1","status":"NOT_MET","explanation":"Pues no."},
                  {"criterionId":"C2","status":"MET","explanation":"Este sí."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, DOS);

        assertThat(parsed.verdicts()).containsOnlyKeys("C2");
        assertThat(parsed.problems().get("C1")).contains("más de un veredicto");
    }

    @Test
    @DisplayName("se ignora un veredicto con un identificador que no se pidió")
    void identificadorInventado() {
        String json =
                """
                {"results":[
                  {"criterionId":"C9","status":"MET","explanation":"De otro ticket."},
                  {"criterionId":"C1","status":"MET","explanation":"De este."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, UNO);

        assertThat(parsed.verdicts()).containsOnlyKeys("C1");
        assertThat(parsed.verdicts().get("C1").explanation()).isEqualTo("De este.");
    }

    @Test
    @DisplayName("tolera que el modelo devuelva el número en vez del identificador")
    void numeroEnVezDeIdentificador() {
        String json = """
                {"results":[{"criterionIndex":2,"status":"MET","explanation":"Sí."}]}
                """;

        assertThat(parser.parse(json, DOS).verdicts()).containsOnlyKeys("C2");
    }

    @Test
    @DisplayName("acepta un veredicto sin evidencia")
    void sinEvidencia() {
        String json = """
                {"results":[{"criterionId":"C1","status":"UNVERIFIED","explanation":"El diff no lo muestra."}]}
                """;

        assertThat(parser.parse(json, UNO).verdicts().get("C1").evidence()).isNull();
    }

    @Test
    @DisplayName("extrae el JSON aunque venga envuelto en un bloque de código")
    void jsonEnvuelto() {
        String json = "```json\n{\"results\":[{\"criterionId\":\"C1\",\"status\":\"MET\",\"explanation\":\"Sí.\"}]}\n```";

        assertThat(parser.parse(json, UNO).verdicts()).hasSize(1);
    }

    @Test
    @DisplayName("acepta un JSON válido precedido de prosa con llaves")
    void prosaConLlavesDelante() {
        String json = "El objeto {results} que devuelvo:\n"
                + "{\"results\":[{\"criterionId\":\"C1\",\"status\":\"MET\",\"explanation\":\"Sí.\"}]}";

        assertThat(parser.parse(json, UNO).verdicts()).hasSize(1);
    }

    @Test
    @DisplayName("rechaza una respuesta que no es JSON")
    void noEsJson() {
        assertThatThrownBy(() -> parser.parse("Lo siento, no puedo revisar este diff.", UNO))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    @DisplayName("rechaza un JSON truncado")
    void jsonTruncado() {
        assertThatThrownBy(() -> parser.parse("{\"results\":[{\"criterionId\":\"C1\",", UNO))
                .isInstanceOf(ModelResponseException.class);
    }

    @Test
    @DisplayName("rechaza una respuesta con dos objetos en vez de quedarse con el primero")
    void dosRespuestas() {
        String json = "{\"results\":[{\"criterionId\":\"C1\",\"status\":\"MET\",\"explanation\":\"Borrador.\"}]}"
                + " {\"results\":[{\"criterionId\":\"C1\",\"status\":\"NOT_MET\",\"explanation\":\"Corregido.\"}]}";

        assertThatThrownBy(() -> parser.parse(json, UNO))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("2 respuestas");
    }

    @Test
    @DisplayName("falla solo cuando no queda ni un veredicto en pie")
    void ningunVeredictoUtilizable() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"QUIZA","explanation":"Pues no sé."},
                  {"criterionId":"C2","status":"MET","explanation":"   "}
                ]}
                """;

        assertThatThrownBy(() -> parser.parse(json, DOS))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("ningún veredicto utilizable");
    }

    @Test
    @DisplayName("un estado desconocido solo tumba a su criterio, y se dice cuál era")
    void estadoDesconocido() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"QUIZA","explanation":"Pues no sé."},
                  {"criterionId":"C2","status":"MET","explanation":"Este sí."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, DOS);

        assertThat(parsed.verdicts()).containsOnlyKeys("C2");
        assertThat(parsed.problems().get("C1")).contains("QUIZA");
    }

    @Test
    @DisplayName("un lado del diff desconocido solo tumba a su criterio")
    void ladoDesconocido() {
        String json =
                """
                {"results":[
                  {"criterionId":"C1","status":"MET","explanation":"Sí.",
                   "evidence":{"file":"a.tsx","side":"ARRIBA","lineNumber":3,"snippet":"x"}},
                  {"criterionId":"C2","status":"MET","explanation":"Este sí."}
                ]}
                """;

        ParsedModelResponse parsed = parser.parse(json, DOS);

        assertThat(parsed.problems().get("C1")).contains("ARRIBA");
        assertThat(parsed.verdicts()).containsOnlyKeys("C2");
    }

    @Test
    @DisplayName("rechaza una respuesta sin la lista «results»")
    void sinResults() {
        assertThatThrownBy(() -> parser.parse("{\"veredictos\":[]}", UNO))
                .isInstanceOf(ModelResponseException.class);
    }
}
