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
 * Llama a la API de Claude desde el backend.
 *
 * <p>La credencial se lee de la variable de entorno {@code ANTHROPIC_API_KEY} y no sale nunca de
 * aquí: el navegador habla solo con este backend.
 *
 * <p>El cliente se construye la primera vez que hace falta, no al arrancar, para que la aplicación
 * levante igualmente sin credencial y la pantalla pueda explicar qué falta en vez de morirse en el
 * arranque.
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
    public String completeJson(String systemPrompt, String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                // Revisar código exige precisión más que rapidez, y en Claude Opus 5.5 el esfuerzo
                // por defecto es «medium».
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.HIGH)
                        .build())
                .addUserMessage(userPrompt)
                .build();

        Message message;
        try {
            message = client().messages().create(params);
        } catch (AnthropicException e) {
            // El detalle va al log, no al navegador: el mensaje del SDK puede traer el cuerpo de
            // error de la API y, con ANTHROPIC_BASE_URL, el host interno de la pasarela.
            log.warn("La llamada al modelo falló", e);
            throw new ModelUnavailableException(
                    "No se pudo hablar con el modelo. Mira el log del servidor para ver el detalle.", e);
        }

        StopReason stopReason = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new ModelResponseException("El modelo declinó revisar este diff.");
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            throw new ModelResponseException(
                    "La respuesta del modelo se cortó por longitud. Prueba con un diff más corto "
                            + "o sube REVIEWER_MAX_TOKENS.");
        }

        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .reduce("", String::concat)
                .strip();

        if (text.isEmpty()) {
            throw new ModelResponseException("El modelo respondió sin texto.");
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
                            "Falta la variable de entorno ANTHROPIC_API_KEY. Mira el archivo .env.example "
                                    + "del repositorio para saber cómo configurarla.");
                }
                AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        // El SDK espera diez minutos por defecto, con un hilo de Tomcat ocupado.
                        .timeout(Duration.ofSeconds(120));
                if (baseUrl != null && !baseUrl.isBlank()) {
                    // Para apuntar a una pasarela propia, o a un servidor de pruebas.
                    builder.baseUrl(baseUrl);
                }
                client = builder.build();
            }
            return client;
        }
    }
}
