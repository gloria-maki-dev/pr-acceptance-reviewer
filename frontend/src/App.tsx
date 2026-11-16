import { useRef, useState } from 'react';
import { parseCriteria, ReviewError, reviewPullRequest } from './api';
import { ResultCard } from './components/ResultCard';
import { DIFF_FIXED, DIFF_WITH_BUG, TICKET } from './examples';
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
      setError(cause instanceof ReviewError ? cause.message : 'Something failed while reviewing the diff.');
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
          Check whether a <code>git diff</code> satisfies a ticket&rsquo;s acceptance criteria, and
          show the exact line each verdict rests on.
        </p>
      </header>

      <main className="workbench">
        <section className="panel panel--input" aria-labelledby="input-title">
          <h2 id="input-title" className="panel__title">
            What to review
          </h2>

          <div className="field">
            <label className="field__label" htmlFor="criteria">
              Acceptance criteria
            </label>
            <textarea
              id="criteria"
              aria-describedby="criteria-hint"
              className="field__input"
              rows={4}
              value={criteriaText}
              onChange={(event) => setCriteriaText(event.target.value)}
              placeholder="One criterion per line."
              spellCheck={false}
            />
            <span className="field__hint" id="criteria-hint">
              One criterion per line. Right now there {criteria.length === 1 ? 'is' : 'are'}{' '}
              {criteria.length}.
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
              placeholder="Paste the output of git diff here."
              spellCheck={false}
            />
          </div>

          <div className="actions">
            <div className="actions__examples">
              <button type="button" className="button" onClick={() => loadExample(DIFF_WITH_BUG)}>
                Try example
              </button>
              <button type="button" className="button" onClick={() => loadExample(DIFF_FIXED)}>
                Load fixed diff
              </button>
            </div>
            <button
              type="button"
              className="button button--primary"
              onClick={handleReview}
              disabled={!canReview}
            >
              {phase === 'loading' ? 'Reviewing…' : 'Review PR'}
            </button>
          </div>

          <p className="actions__ticket">
            The example uses ticket {TICKET.id}, &ldquo;{TICKET.title}&rdquo;, from an online store.
          </p>
        </section>

        <section className="panel panel--output" aria-labelledby="output-title">
          <h2 id="output-title" className="panel__title">
            Result
          </h2>

          <div aria-live="polite" aria-busy={phase === 'loading'} className="output">
            {phase === 'loading' && (
              <p className="placeholder placeholder--busy">
                <span className="spinner" aria-hidden="true" />
                Checking each criterion against the diff.
              </p>
            )}

            {phase === 'error' && error && (
              <p className="placeholder placeholder--error" role="alert">
                {error}
              </p>
            )}

            {phase === 'idle' && (
              <p className="placeholder">
                Paste a diff and press &ldquo;Review PR&rdquo;. Or load the {TICKET.id} example.
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
                    Reviewed by {result.model}, via {result.provider}.
                  </p>
                ) : (
                  <p className="output__model output__model--warning" role="note">
                    <strong>This was not reviewed by the Anthropic API.</strong> &ldquo;
                    {result.provider}&rdquo; answered, so these verdicts are not a real judgement
                    of your diff. Start the backend with an ANTHROPIC_API_KEY and without
                    ANTHROPIC_BASE_URL to review it for real.
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
