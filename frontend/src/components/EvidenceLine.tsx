import { SIDE_LABEL, SIDE_MARKER, type Evidence } from '../types';

/**
 * The citation, drawn as the diff line it is: gutter with the number, marker, and the snippet.
 *
 * Showing it this way is the point of the project: the reviewer can go to the diff, find that
 * file and that line, and check in a second whether the evidence supports the explanation.
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
