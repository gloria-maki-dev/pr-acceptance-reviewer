package dev.gloria.pracceptance.model;

/** Puerta de salida hacia el modelo. Tenerla como interfaz deja probar todo lo demás sin red. */
public interface ModelClient {

    /**
     * Manda los dos prompts al modelo y devuelve su respuesta en bruto, que se espera que sea JSON.
     *
     * @throws ModelUnavailableException si no se pudo llegar al modelo
     */
    String completeJson(String systemPrompt, String userPrompt);

    /** Identificador del modelo, para mostrarlo en la pantalla junto al resultado. */
    String modelName();
}
