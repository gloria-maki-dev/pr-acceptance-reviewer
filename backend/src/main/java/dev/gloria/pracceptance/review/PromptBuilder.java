package dev.gloria.pracceptance.review;

import dev.gloria.pracceptance.diff.ParsedDiff;
import java.util.List;
import org.springframework.stereotype.Component;

/** Arma los dos prompts que se le mandan al modelo. */
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT =
            """
            Eres quien revisa una pull request. Te dan los criterios de aceptación de un ticket y un
            diff ya anotado, y decides si el diff cumple cada criterio.

            El diff llega con una línea por cambio, con esta forma:

                ARCHIVO: ruta/del/archivo.tsx
                [ADDED 20] contenido de la línea 20 del archivo nuevo
                [REMOVED 20] contenido de la línea 20 del archivo antiguo
                [CONTEXT 18] línea sin cambios, numerada como en el archivo nuevo

            Reglas, en orden de importancia:

            1. Para cada criterio devuelve exactamente un veredicto, con uno de estos estados:
               - MET: el diff cumple el criterio, y puedes señalar la línea que lo demuestra.
               - NOT_MET: el diff incumple el criterio, y puedes señalar la línea que lo demuestra.
               - UNVERIFIED: el diff no da contexto suficiente para decidir.
            2. MET y NOT_MET exigen una cita. Si no puedes señalar una línea concreta del diff,
               el estado es UNVERIFIED.
            3. Las citas se copian del diff anotado, nunca se inventan: «file» es la ruta que
               aparece tras ARCHIVO, «side» es ADDED, REMOVED o CONTEXT, «lineNumber» es el número
               que va entre corchetes y «snippet» es texto copiado literalmente de esa línea.
               Una cita que no se corresponda con el diff se descarta y el criterio queda
               como No verificado, así que no arriesgues: si dudas, usa UNVERIFIED.
            4. El diff solo muestra los trozos que cambian. No supongas nada del resto del archivo
               ni de archivos que no aparecen. Si un criterio habla de que algo NO debe cambiar y
               ese algo no aparece en el diff, está cumplido: el diff es todo lo que cambia.
            5. «explanation» es una frase en español, breve y concreta, que diga qué viste. Nada de
               repetir el criterio ni de hablar de ti mismo.

            Responde únicamente con un objeto JSON, sin texto alrededor y sin bloque de código:

            {"results":[{"criterionIndex":1,"status":"MET","explanation":"...",
              "evidence":{"file":"...","side":"ADDED","lineNumber":1,"snippet":"..."}}]}

            «evidence» vale null cuando el estado es UNVERIFIED.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public String userPrompt(List<String> criteria, ParsedDiff diff) {
        StringBuilder out = new StringBuilder("CRITERIOS DE ACEPTACIÓN\n\n");
        for (int i = 0; i < criteria.size(); i++) {
            out.append(i + 1).append(". ").append(criteria.get(i).strip()).append('\n');
        }
        out.append("\nDIFF ANOTADO\n\n").append(diff.toAnnotatedText());
        out.append("\nDevuelve ")
                .append(criteria.size())
                .append(criteria.size() == 1 ? " veredicto" : " veredictos")
                .append(", uno por criterio, en el JSON acordado.\n");
        return out.toString();
    }
}
