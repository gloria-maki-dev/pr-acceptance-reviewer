import type { ReviewResponse } from './types';

/** Un error que ya trae un mensaje pensado para enseñar en la pantalla. */
export class ReviewError extends Error {}

interface ApiErrorBody {
  error?: string;
}

/**
 * Manda la revisión al backend.
 *
 * <p>La llamada sale siempre contra el mismo origen: en desarrollo, Vite hace de proxy hacia el
 * backend, así que la credencial del modelo no pasa nunca por el navegador.
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
      'No llego al backend. Arráncalo con «mvn spring-boot:run» en la carpeta backend/.',
    );
  }

  // Si la petición se cancela entre las cabeceras y el cuerpo, json() rechaza con AbortError:
  // tragárselo convertiría una cancelación en un error falso en pantalla.
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
    throw new ReviewError(message ?? `El servidor respondió ${response.status}.`);
  }

  const parsed = body as ReviewResponse | null;
  if (!parsed || !Array.isArray(parsed.results)) {
    throw new ReviewError('El servidor devolvió una respuesta que no entiendo.');
  }
  return parsed;
}

/** Un criterio por línea; las líneas vacías no cuentan. */
export function parseCriteria(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}
