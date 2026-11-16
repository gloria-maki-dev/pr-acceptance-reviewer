import type { ReviewResponse } from './types';

/** An error that already carries a message meant to be shown on screen. */
export class ReviewError extends Error {}

interface ApiErrorBody {
  error?: string;
}

/**
 * Sends the review to the backend.
 *
 * The call always goes to the same origin: in development Vite proxies it to the backend, so the
 * model credential never passes through the browser.
 */
export async function reviewPullRequest(
  acceptanceCriteria: string[],
  diff: string,
  signal?: AbortSignal,
): Promise<ReviewResponse> {
  let response: Response;
  try {
    response = await fetch('/api/reviews', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ acceptanceCriteria, diff }),
      signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw new ReviewError(
      'Cannot reach the backend. Start it with `mvn spring-boot:run` in the backend/ folder.',
    );
  }

  // If the request is aborted between the headers and the body, json() rejects with AbortError.
  // Swallowing it would turn a cancellation into a bogus error on screen.
  let body: unknown = null;
  try {
    body = await response.json();
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    body = null;
  }

  if (!response.ok) {
    const message = (body as ApiErrorBody | null)?.error;
    throw new ReviewError(message ?? `The server responded ${response.status}.`);
  }

  const parsed = body as ReviewResponse | null;
  if (!parsed || !Array.isArray(parsed.results)) {
    throw new ReviewError('The server returned an answer we cannot read.');
  }
  return parsed;
}

/** One criterion per line; blank lines do not count. */
export function parseCriteria(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}
