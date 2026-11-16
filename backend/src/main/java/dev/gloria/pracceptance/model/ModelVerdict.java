package dev.gloria.pracceptance.model;

import dev.gloria.pracceptance.review.Evidence;
import dev.gloria.pracceptance.review.ReviewStatus;

/**
 * Lo que el modelo dice de un criterio, antes de comprobar nada.
 *
 * <p>Separar esto de {@link dev.gloria.pracceptance.review.CriterionResult} es el centro del
 * proyecto: el veredicto del modelo es una propuesta, y solo pasa a ser un resultado cuando la
 * cita se ha podido localizar en el diff.
 */
public record ModelVerdict(ReviewStatus status, String explanation, Evidence evidence) {}
