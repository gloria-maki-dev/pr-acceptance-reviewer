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

    /** Quién atendió de verdad la petición: la API de Claude, o lo que sea que haya en su lugar. */
    default String providerDescription() {
        return ProviderLabel.OFFICIAL;
    }

    /** {@code false} si la petición no fue a la API de Claude. */
    default boolean isOfficialProvider() {
        return true;
    }
}
