import type { CriterionResult } from '../types';
import { EvidenceLine } from './EvidenceLine';
import { StatusChip } from './StatusChip';

/**
 * Un criterio con su veredicto y, si se sostiene, su evidencia.
 *
 * <p>Cuando el backend rechaza la cita, aquí NO se enseña ni la cita ni la explicación del
 * modelo. La explicación describía una línea que no está en este diff: dejarla a la vista, aunque
 * sea con la etiqueta «No verificado» al lado, es justo lo que hace que alguien se la crea.
 */
export function ResultCard({ result, index }: { result: CriterionResult; index: number }) {
  const citationRejected = result.verificationNote !== null;

  return (
    <article className={`result result--${result.status.toLowerCase()}`}>
      <header className="result__head">
        <span className="result__number" aria-hidden="true">
          {index + 1}
        </span>
        <h3 className="result__criterion">{result.criterion}</h3>
        <StatusChip status={result.status} />
      </header>

      {citationRejected ? (
        <p className="result__note">
          <strong>Sin evidencia que lo sostenga.</strong> {result.verificationNote} No se muestran
          ni la cita ni la explicación del modelo, porque se apoyaban en ella.
        </p>
      ) : (
        <>
          <p className="result__explanation">{result.explanation}</p>
          {result.evidence ? (
            <EvidenceLine evidence={result.evidence} />
          ) : (
            <p className="result__no-evidence">El modelo no citó ninguna línea del diff.</p>
          )}
        </>
      )}
    </article>
  );
}
