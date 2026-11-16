package dev.gloria.pracceptance.review;

import dev.gloria.pracceptance.diff.ParsedDiff;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Arma los dos prompts que se le mandan al modelo. */
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT =
            """
            Eres quien revisa una pull request. Te dan los criterios de aceptación de un ticket y un
            diff ya anotado, y decides si el diff cumple cada criterio.

            Cada criterio llega con un identificador (C1, C2, C3...). El diff llega con una línea
            por cambio, con esta forma:

                ARCHIVO: ruta/del/archivo.tsx
                [ADDED 20] contenido de la línea 20 del archivo nuevo
                [REMOVED 20] contenido de la línea 20 del archivo antiguo
                [CONTEXT 18] línea sin cambios, numerada como en el archivo nuevo

            Reglas, en orden de importancia:

            1. Devuelve un veredicto por cada identificador, sin saltarte ninguno, aunque no tengas
               nada que decir de él: en ese caso el estado es UNVERIFIED. Los estados son:
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

            {"results":[{"criterionId":"C1","status":"MET","explanation":"...",
              "evidence":{"file":"...","side":"ADDED","lineNumber":1,"snippet":"..."}}]}

            «evidence» vale null cuando el estado es UNVERIFIED.
            """;

    public String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    /** Los identificadores que se le mandan al modelo, en el orden de los criterios. */
    public static List<String> criterionIds(int criteriaCount) {
        List<String> ids = new ArrayList<>(criteriaCount);
        for (int i = 1; i <= criteriaCount; i++) {
            ids.add("C" + i);
        }
        return List.copyOf(ids);
    }

    public String userPrompt(List<String> criteria, ParsedDiff diff) {
        List<String> ids = criterionIds(criteria.size());

        StringBuilder out = new StringBuilder("CRITERIOS DE ACEPTACIÓN\n\n");
        for (int i = 0; i < criteria.size(); i++) {
            out.append(ids.get(i)).append(". ").append(criteria.get(i).strip()).append('\n');
        }
        out.append("\nDIFF ANOTADO\n\n").append(diff.toAnnotatedText());
        out.append("\nDevuelve un veredicto para cada uno de estos identificadores: ")
                .append(String.join(", ", ids))
                .append(".\n");
        return out.toString();
    }
}
