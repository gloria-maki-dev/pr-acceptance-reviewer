package dev.gloria.pracceptance.review;

/**
 * El resultado de un criterio, ya comprobado contra el diff.
 *
 * @param criterion el criterio de aceptación tal cual lo escribió quien revisa
 * @param status veredicto final, después de comprobar la cita
 * @param explanation la explicación del modelo, que se muestra siempre junto a la cita
 * @param evidence la cita del modelo, o {@code null} si no citó nada
 * @param verificationNote por qué no se pudo verificar la cita, o {@code null} si todo cuadró
 */
public record CriterionResult(
        String criterion, ReviewStatus status, String explanation, Evidence evidence, String verificationNote) {}
