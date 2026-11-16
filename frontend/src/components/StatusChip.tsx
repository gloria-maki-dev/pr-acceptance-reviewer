import { STATUS_LABEL, type ReviewStatus } from '../types';

/** La pastilla con el veredicto. El color lo pone la clase del estado. */
export function StatusChip({ status }: { status: ReviewStatus }) {
  return <span className={`chip chip--${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>;
}
