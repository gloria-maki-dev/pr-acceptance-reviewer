import { useRef, useState } from 'react';
import { parseCriteria, ReviewError, reviewPullRequest } from './api';
import { ResultCard } from './components/ResultCard';
import { DIFF_CON_FALLO, DIFF_CORREGIDO, TICKET } from './examples';
import type { ReviewResponse } from './types';

type Phase = 'idle' | 'loading' | 'done' | 'error';

export function App() {
  const [criteriaText, setCriteriaText] = useState(TICKET.criteria);
  const [diffText, setDiffText] = useState('');
  const [phase, setPhase] = useState<Phase>('idle');
  const [result, setResult] = useState<ReviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const inFlight = useRef<AbortController | null>(null);

  const criteria = parseCriteria(criteriaText);
  const canReview = criteria.length > 0 && diffText.trim().length > 0 && phase !== 'loading';

  function loadExample(diff: string) {
    inFlight.current?.abort();
    setCriteriaText(TICKET.criteria);
    setDiffText(diff);
    setResult(null);
    setError(null);
    setPhase('idle');
  }

  async function handleReview() {
    const controller = new AbortController();
    inFlight.current?.abort();
    inFlight.current = controller;

    setPhase('loading');
    setError(null);

    try {
      const response = await reviewPullRequest(criteria, diffText, controller.signal);
      setResult(response);
      setPhase('done');
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') {
        return;
      }
      setResult(null);
      setError(cause instanceof ReviewError ? cause.message : 'Algo falló al revisar el diff.');
      setPhase('error');
    } finally {
      if (inFlight.current === controller) {
        inFlight.current = null;
      }
    }
  }

  return (
    <div className="page">
      <header className="masthead">
        <h1 className="masthead__title">PR Acceptance Reviewer</h1>
        <p className="masthead__lead">
          Comprueba si un <code>git diff</code> cumple los criterios de aceptación de un ticket, y
          enseña la línea exacta en la que se apoya cada veredicto.
        </p>
      </header>

      <main className="workbench">
        <section className="panel panel--input" aria-labelledby="titulo-entrada">
          <h2 id="titulo-entrada" className="panel__title">
            Lo que hay que revisar
          </h2>

          <div className="field">
            <label className="field__label" htmlFor="criterios">
              Criterios de aceptación
            </label>
            <textarea
              id="criterios"
              aria-describedby="criterios-pista"
              className="field__input"
              rows={4}
              value={criteriaText}
              onChange={(event) => setCriteriaText(event.target.value)}
              placeholder="Un criterio por línea."
              spellCheck={false}
            />
            <span className="field__hint" id="criterios-pista">
              Un criterio por línea. Ahora mismo hay {criteria.length}.
            </span>
          </div>

          <div className="field field--grow">
            <label className="field__label" htmlFor="diff">
              Diff
            </label>
            <textarea
              id="diff"
              className="field__input field__input--code"
              rows={16}
              value={diffText}
              onChange={(event) => setDiffText(event.target.value)}
              placeholder="Pega aquí la salida de git diff."
              spellCheck={false}
            />
          </div>

          <div className="actions">
            <div className="actions__examples">
              <button type="button" className="button" onClick={() => loadExample(DIFF_CON_FALLO)}>
                Probar ejemplo
              </button>
              <button type="button" className="button" onClick={() => loadExample(DIFF_CORREGIDO)}>
                Cargar diff corregido
              </button>
            </div>
            <button
              type="button"
              className="button button--primary"
              onClick={handleReview}
              disabled={!canReview}
            >
              {phase === 'loading' ? 'Revisando…' : 'Revisar PR'}
            </button>
          </div>

          <p className="actions__ticket">
            El ejemplo usa el ticket {TICKET.id}, «{TICKET.title}», de una tienda online.
          </p>
        </section>

        <section className="panel panel--output" aria-labelledby="titulo-resultado">
          <h2 id="titulo-resultado" className="panel__title">
            Resultado
          </h2>

          <div aria-live="polite" aria-busy={phase === 'loading'} className="output">
            {phase === 'loading' && (
              <p className="placeholder placeholder--busy">
                <span className="spinner" aria-hidden="true" />
                Comprobando cada criterio contra el diff.
              </p>
            )}

            {phase === 'error' && error && (
              <p className="placeholder placeholder--error" role="alert">
                {error}
              </p>
            )}

            {phase === 'idle' && (
              <p className="placeholder">
                Pega un diff y pulsa «Revisar PR». O carga el ejemplo del ticket {TICKET.id}.
              </p>
            )}

            {phase === 'done' && result && (
              <>
                <ol className="results">
                  {result.results.map((item, index) => (
                    <li key={`${index}-${item.criterion}`}>
                      <ResultCard result={item} index={index} />
                    </li>
                  ))}
                </ol>
                {result.officialProvider ? (
                  <p className="output__model">
                    Revisado por {result.model}, vía {result.provider}.
                  </p>
                ) : (
                  <p className="output__model output__model--warning" role="note">
                    <strong>Esto no lo ha revisado la API de Claude.</strong> Respondió «
                    {result.provider}», así que estos veredictos no son un juicio real sobre tu
                    diff. Arranca el backend con una ANTHROPIC_API_KEY y sin ANTHROPIC_BASE_URL
                    para revisarlo de verdad.
                  </p>
                )}
              </>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}
