package dev.gloria.pracceptance.model;

import java.util.Map;

/**
 * Lo que se pudo aprovechar de la respuesta del modelo.
 *
 * <p>Una respuesta a medias no invalida la revisión entera. Los veredictos que se entienden se
 * conservan; de los que no, se guarda el motivo para poder decirle a quien revisa qué pasó con
 * ese criterio en concreto. Un criterio que no aparece en ninguno de los dos mapas es un criterio
 * que el modelo sencillamente no evaluó.
 *
 * @param verdicts veredictos utilizables, por identificador de criterio
 * @param problems por qué no se pudo usar el veredicto de un criterio, por identificador
 */
public record ParsedModelResponse(Map<String, ModelVerdict> verdicts, Map<String, String> problems) {

    public ParsedModelResponse {
        verdicts = Map.copyOf(verdicts);
        problems = Map.copyOf(problems);
    }
}
