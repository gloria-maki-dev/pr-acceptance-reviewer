package dev.gloria.pracceptance.model;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * What to call whatever is on the other end.
 *
 * <p>This exists because a screen that says "Reviewed by claude-opus-5-5" while a test server on
 * localhost is answering is lying, and it is an expensive lie: the reviewer believes verdicts
 * nobody thought about. If the request did not go to the Anthropic API, it is said here.
 */
public final class ProviderLabel {

    public static final String OFFICIAL = "Anthropic API";

    private ProviderLabel() {
    }

    /** {@code true} only when the API has not been pointed somewhere else. */
    public static boolean isOfficial(String baseUrl) {
        return baseUrl == null || baseUrl.isBlank();
    }

    /** An honest name to show next to the result. */
    public static String describe(String baseUrl) {
        if (isOfficial(baseUrl)) {
            return OFFICIAL;
        }
        String host = host(baseUrl);
        return (isLocal(host) ? "Simulated provider at " : "Alternative provider at ") + host;
    }

    private static boolean isLocal(String host) {
        String name = host.split(":")[0];
        return "localhost".equals(name) || "127.0.0.1".equals(name) || "[::1]".equals(name) || "::1".equals(name);
    }

    private static String host(String baseUrl) {
        try {
            URI uri = new URI(baseUrl.strip());
            if (uri.getHost() == null) {
                return baseUrl.strip();
            }
            return uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
        } catch (URISyntaxException e) {
            return baseUrl.strip();
        }
    }
}
