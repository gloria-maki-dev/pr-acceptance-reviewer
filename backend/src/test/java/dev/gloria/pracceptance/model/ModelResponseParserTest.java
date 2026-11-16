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

    private static final String VALIDA =
            """
            {
              "results": [
                {
                  "criterionIndex": 1,
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
                  "criterionIndex": 2,
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
    @DisplayName("convierte una respuesta válida en veredictos ordenados por criterio")
    void respuestaValida() {
        List<ModelVerdict> verdicts = parser.parse(VALIDA, 2);

        assertThat(verdicts).hasSize(2);
        assertThat(verdicts.get(0).status()).isEqualTo(ReviewStatus.MET);
        assertThat(verdicts.get(0).evidence().side()).isEqualTo(DiffSide.ADDED);
        assertThat(verdicts.get(0).evidence().lineNumber()).isEqualTo(20);
        assertThat(verdicts.get(1).status()).isEqualTo(ReviewStatus.NOT_MET);
        assertThat(verdicts.get(1).evidence().file()).isEqualTo("src/components/OrderHistory.tsx");
    }

    @Test
    @DisplayName("reordena los veredictos si el modelo los devuelve desordenados")
    void reordena() {
        String json =
                """
                {"results":[
                  {"criterionIndex":2,"status":"UNVERIFIED","explanation":"Segundo","evidence":null},
                  {"criterionIndex":1,"status":"MET","explanation":"Primero","evidence":null}
                ]}
                """;

        List<ModelVerdict> verdicts = parser.parse(json, 2);

        assertThat(verdicts).extracting(ModelVerdict::explanation).containsExactly("Primero", "Segundo");
    }

    @Test
    @DisplayName("acepta un veredicto sin evidencia")
    void sinEvidencia() {
        String json = """
                {"results":[{"criterionIndex":1,"status":"UNVERIFIED","explanation":"El diff no lo muestra."}]}
                """;

        List<ModelVerdict> verdicts = parser.parse(json, 1);

        assertThat(verdicts.get(0).evidence()).isNull();
    }

    @Test
    @DisplayName("extrae el JSON aunque venga envuelto en un bloque de código")
    void jsonEnvuelto() {
        String json = "```json\n{\"results\":[{\"criterionIndex\":1,\"status\":\"MET\",\"explanation\":\"Sí.\"}]}\n```";

        assertThat(parser.parse(json, 1)).hasSize(1);
    }

    @Test
    @DisplayName("rechaza una respuesta que no es JSON")
    void noEsJson() {
        assertThatThrownBy(() -> parser.parse("Lo siento, no puedo revisar este diff.", 1))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    @DisplayName("rechaza un JSON truncado")
    void jsonTruncado() {
        assertThatThrownBy(() -> parser.parse("{\"results\":[{\"criterionIndex\":1,", 1))
                .isInstanceOf(ModelResponseException.class);
    }

    @Test
    @DisplayName("rechaza un estado que no existe")
    void estadoDesconocido() {
        String json = """
                {"results":[{"criterionIndex":1,"status":"QUIZA","explanation":"Pues no sé."}]}
                """;

        assertThatThrownBy(() -> parser.parse(json, 1))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("QUIZA");
    }

    @Test
    @DisplayName("rechaza una respuesta con menos veredictos que criterios")
    void faltanVeredictos() {
        String json = """
                {"results":[{"criterionIndex":1,"status":"MET","explanation":"Sí."}]}
                """;

        assertThatThrownBy(() -> parser.parse(json, 2))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("2");
    }

    @Test
    @DisplayName("rechaza índices de criterio repetidos o fuera de rango")
    void indicesInvalidos() {
        String json = """
                {"results":[
                  {"criterionIndex":1,"status":"MET","explanation":"Sí."},
                  {"criterionIndex":1,"status":"MET","explanation":"Otra vez."}
                ]}
                """;

        assertThatThrownBy(() -> parser.parse(json, 2)).isInstanceOf(ModelResponseException.class);
    }

    @Test
    @DisplayName("rechaza una evidencia incompleta")
    void evidenciaIncompleta() {
        String json =
                """
                {"results":[{"criterionIndex":1,"status":"MET","explanation":"Sí.",
                 "evidence":{"file":"a.tsx","side":"ADDED","snippet":"x"}}]}
                """;

        assertThatThrownBy(() -> parser.parse(json, 1))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("lineNumber");
    }

    @Test
    @DisplayName("rechaza una explicación vacía")
    void explicacionVacia() {
        String json = """
                {"results":[{"criterionIndex":1,"status":"MET","explanation":"   "}]}
                """;

        assertThatThrownBy(() -> parser.parse(json, 1)).isInstanceOf(ModelResponseException.class);
    }

    @Test
    @DisplayName("rechaza un lado del diff desconocido")
    void ladoDesconocido() {
        String json =
                """
                {"results":[{"criterionIndex":1,"status":"MET","explanation":"Sí.",
                 "evidence":{"file":"a.tsx","side":"ARRIBA","lineNumber":3,"snippet":"x"}}]}
                """;

        assertThatThrownBy(() -> parser.parse(json, 1))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("ARRIBA");
    }

    @Test
    @DisplayName("rechaza una respuesta con dos objetos en vez de quedarse con el primero")
    void dosRespuestas() {
        String json = "{\"results\":[{\"criterionIndex\":1,\"status\":\"MET\",\"explanation\":\"Borrador.\"}]}"
                + " {\"results\":[{\"criterionIndex\":1,\"status\":\"NOT_MET\",\"explanation\":\"Corregido.\"}]}";

        assertThatThrownBy(() -> parser.parse(json, 1))
                .isInstanceOf(ModelResponseException.class)
                .hasMessageContaining("2 respuestas");
    }

    @Test
    @DisplayName("acepta un JSON válido precedido de prosa con llaves")
    void prosaConLlavesDelante() {
        String json = "El objeto {results} que devuelvo:\n"
                + "{\"results\":[{\"criterionIndex\":1,\"status\":\"MET\",\"explanation\":\"Sí.\"}]}";

        assertThat(parser.parse(json, 1)).hasSize(1);
    }
}
