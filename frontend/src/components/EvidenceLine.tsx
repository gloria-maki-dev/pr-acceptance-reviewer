import { SIDE_LABEL, SIDE_MARKER, type Evidence } from '../types';

/**
 * La cita, dibujada como la línea de diff que es: medianil con el número, marcador y el fragmento.
 *
 * <p>Enseñarla así es el objetivo del proyecto: quien revisa puede ir al diff, buscar ese archivo y
 * esa línea, y comprobar en un segundo si la evidencia sostiene lo que dice la explicación.
 */
export function EvidenceLine({ evidence }: { evidence: Evidence }) {
  const side = evidence.side.toLowerCase();
  return (
    <figure className="evidence">
      <figcaption className="evidence__file">
        {evidence.file}
        <span className="evidence__side">{SIDE_LABEL[evidence.side]}</span>
      </figcaption>
      <pre className={`evidence__line evidence__line--${side}`}>
        <span className="evidence__gutter" aria-hidden="true">
          {evidence.lineNumber}
        </span>
        <span className="evidence__marker" aria-hidden="true">
          {SIDE_MARKER[evidence.side]}
        </span>
        <code>{evidence.snippet}</code>
      </pre>
    </figure>
  );
}
