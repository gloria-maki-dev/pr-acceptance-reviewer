package dev.gloria.pracceptance.model;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Cómo se llama lo que hay al otro lado.
 *
 * <p>Existe porque una pantalla que dice «Revisado por claude-opus-5-5» cuando detrás hay un
 * servidor de pruebas en localhost miente, y es una mentira cara: quien revisa se cree unos
 * veredictos que nadie ha pensado. Si la petición no va a la API de Claude, aquí se dice.
 */
public final class ProviderLabel {

    public static final String OFFICIAL = "API de Claude";

    private ProviderLabel() {
    }

    /** {@code true} solo si no se ha desviado la API a otro sitio. */
    public static boolean isOfficial(String baseUrl) {
        return baseUrl == null || baseUrl.isBlank();
    }

    /** Un nombre honesto para enseñar junto al resultado. */
    public static String describe(String baseUrl) {
        if (isOfficial(baseUrl)) {
            return OFFICIAL;
        }
        String host = host(baseUrl);
        return (isLocal(host) ? "Proveedor simulado en " : "Proveedor alternativo en ") + host;
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
