import { STATUS_LABEL, type ReviewStatus } from '../types';

/** The chip carrying the verdict. Its colour comes from the status class. */
export function StatusChip({ status }: { status: ReviewStatus }) {
  return <span className={`chip chip--${status.toLowerCase()}`}>{STATUS_LABEL[status]}</span>;
}
