import type { CriterionResult } from '../types';
import { EvidenceLine } from './EvidenceLine';
import { StatusChip } from './StatusChip';

/**
 * One criterion with its verdict and, when it holds up, its evidence.
 *
 * When the backend rejects the citation, neither the citation nor the model's explanation is
 * shown here. The explanation described a line that is not in this diff: leaving it on screen,
 * even with a "Not verified" label beside it, is exactly what makes someone believe it.
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
          <strong>{result.explanation === null ? 'Not checked.' : 'No evidence to back it.'}</strong>{' '}
          {result.verificationNote}
          {result.explanation !== null &&
            ' The citation and the model’s explanation are hidden, because they rested on it.'}
        </p>
      ) : (
        <>
          <p className="result__explanation">{result.explanation}</p>
          {result.evidence ? (
            <EvidenceLine evidence={result.evidence} />
          ) : (
            <p className="result__no-evidence">The model cited no line of the diff.</p>
          )}
        </>
      )}
    </article>
  );
}
