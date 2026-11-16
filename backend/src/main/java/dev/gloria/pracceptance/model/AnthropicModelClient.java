package dev.gloria.pracceptance.model;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Calls the Anthropic API from the backend.
 *
 * <p>The credential is read from the {@code ANTHROPIC_API_KEY} environment variable and never
 * leaves this class: the browser only ever talks to this backend.
 *
 * <p>The client is built the first time it is needed rather than at startup, so the application
 * comes up without a credential and the screen can explain what is missing instead of dying on
 * boot.
 */
@Component
public class AnthropicModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicModelClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final long maxTokens;
    private volatile AnthropicClient client;

    public AnthropicModelClient(
            @Value("${ANTHROPIC_API_KEY:}") String apiKey,
            @Value("${ANTHROPIC_BASE_URL:}") String baseUrl,
            @Value("${reviewer.model}") String model,
            @Value("${reviewer.max-tokens}") long maxTokens) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public String providerDescription() {
        return ProviderLabel.describe(baseUrl);
    }

    @Override
    public boolean isOfficialProvider() {
        return ProviderLabel.isOfficial(baseUrl);
    }

    @Override
    public String completeJson(String systemPrompt, String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                // Reviewing code calls for precision over speed, and on Claude Opus 5.5 the
                // default effort is "medium".
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.HIGH)
                        .build())
                .addUserMessage(userPrompt)
                .build();

        Message message;
        try {
            message = client().messages().create(params);
        } catch (AnthropicException e) {
            // The detail goes to the log, not the browser: the SDK message can carry the API's
            // error body and, with ANTHROPIC_BASE_URL, the gateway's internal host.
            log.warn("The call to the model failed", e);
            throw new ModelUnavailableException(
                    "Could not reach the model. See the server log for the detail.", e);
        }

        StopReason stopReason = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new ModelResponseException("The model declined to review this diff.");
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            throw new ModelResponseException(
                    "The model's answer was cut off by length. Try a shorter diff, or raise "
                            + "REVIEWER_MAX_TOKENS.");
        }

        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .reduce("", String::concat)
                .strip();

        if (text.isEmpty()) {
            throw new ModelResponseException("The model answered with no text.");
        }
        return text;
    }

    private AnthropicClient client() {
        AnthropicClient existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                if (apiKey == null || apiKey.isBlank()) {
                    throw new ModelUnavailableException(
                            "The ANTHROPIC_API_KEY environment variable is missing. See .env.example "
                                    + "in the repository for how to set it.");
                }
                AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        // The SDK waits ten minutes by default, with a Tomcat thread tied up.
                        .timeout(Duration.ofSeconds(120));
                if (baseUrl != null && !baseUrl.isBlank()) {
                    // To point at a gateway of your own, or at a test server.
                    builder.baseUrl(baseUrl);
                }
                client = builder.build();
            }
            return client;
        }
    }
}
