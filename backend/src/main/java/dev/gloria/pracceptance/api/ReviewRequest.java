package dev.gloria.pracceptance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lo que manda la pantalla: los criterios de aceptación y el diff.
 *
 * <p>Los topes no son decorativos: evitan mandarle al modelo un diff de medio repositorio por un
 * pegado accidental.
 */
public record ReviewRequest(
        @NotEmpty(message = "Hace falta al menos un criterio de aceptación.")
                @Size(max = 20, message = "Como mucho 20 criterios por revisión.")
                List<
                                @NotBlank(message = "Hay un criterio vacío.")
                                @Size(max = 1000, message = "Un criterio no puede pasar de 1000 caracteres.")
                                String>
                        acceptanceCriteria,
        @NotBlank(message = "Hace falta pegar el diff.")
                @Size(max = 200_000, message = "El diff no puede pasar de 200 000 caracteres.")
                String diff) {}
