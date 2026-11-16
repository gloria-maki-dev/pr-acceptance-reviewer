package dev.gloria.pracceptance.model;

/** The way out to the model. Keeping it an interface is what lets everything else be tested offline. */
public interface ModelClient {

    /**
     * Sends both prompts to the model and returns its raw answer, which is expected to be JSON.
     *
     * @throws ModelUnavailableException if the model could not be reached
     */
    String completeJson(String systemPrompt, String userPrompt);

    /** The model identifier, to show on screen next to the result. */
    String modelName();

    /** Who actually answered: the Anthropic API, or whatever stands in its place. */
    default String providerDescription() {
        return ProviderLabel.OFFICIAL;
    }

    /** {@code false} when the request did not go to the Anthropic API. */
    default boolean isOfficialProvider() {
        return true;
    }
}
