package dev.gloria.pracceptance.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderLabelTest {

    @Test
    @DisplayName("with no base URL the request goes to the Anthropic API")
    void withoutBaseUrl() {
        assertThat(ProviderLabel.isOfficial(null)).isTrue();
        assertThat(ProviderLabel.isOfficial("  ")).isTrue();
        assertThat(ProviderLabel.describe(null)).isEqualTo("Anthropic API");
    }

    @Test
    @DisplayName("a server on localhost is called a simulated provider, with its port")
    void localhost() {
        assertThat(ProviderLabel.isOfficial("http://localhost:9191")).isFalse();
        assertThat(ProviderLabel.describe("http://localhost:9191"))
                .isEqualTo("Simulated provider at localhost:9191");
        assertThat(ProviderLabel.describe("http://127.0.0.1:8081"))
                .isEqualTo("Simulated provider at 127.0.0.1:8081");
    }

    @Test
    @DisplayName("any other destination is called an alternative provider, not a simulated one")
    void ownGateway() {
        assertThat(ProviderLabel.describe("https://gateway.internal.example.com/v1"))
                .isEqualTo("Alternative provider at gateway.internal.example.com");
    }

    @Test
    @DisplayName("an unreadable URL is shown as-is, without pretending it is the official one")
    void unreadableUrl() {
        assertThat(ProviderLabel.isOfficial("not a url")).isFalse();
        assertThat(ProviderLabel.describe("not a url")).contains("not a url");
    }
}
