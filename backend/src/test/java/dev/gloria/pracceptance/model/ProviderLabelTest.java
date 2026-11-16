package dev.gloria.pracceptance.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderLabelTest {

    @Test
    @DisplayName("sin base URL, la petición va a la API de Claude")
    void sinBaseUrl() {
        assertThat(ProviderLabel.isOfficial(null)).isTrue();
        assertThat(ProviderLabel.isOfficial("  ")).isTrue();
        assertThat(ProviderLabel.describe(null)).isEqualTo("API de Claude");
    }

    @Test
    @DisplayName("un servidor en localhost se llama proveedor simulado, con su puerto")
    void localhost() {
        assertThat(ProviderLabel.isOfficial("http://localhost:9191")).isFalse();
        assertThat(ProviderLabel.describe("http://localhost:9191"))
                .isEqualTo("Proveedor simulado en localhost:9191");
        assertThat(ProviderLabel.describe("http://127.0.0.1:8081"))
                .isEqualTo("Proveedor simulado en 127.0.0.1:8081");
    }

    @Test
    @DisplayName("cualquier otro destino se llama proveedor alternativo, no simulado")
    void pasarelaPropia() {
        assertThat(ProviderLabel.describe("https://pasarela.interna.example.com/v1"))
                .isEqualTo("Proveedor alternativo en pasarela.interna.example.com");
    }

    @Test
    @DisplayName("una URL que no se puede leer se enseña tal cual, sin fingir que es la oficial")
    void urlIlegible() {
        assertThat(ProviderLabel.isOfficial("no es una url")).isFalse();
        assertThat(ProviderLabel.describe("no es una url")).contains("no es una url");
    }
}
