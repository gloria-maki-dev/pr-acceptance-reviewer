import criteriaRaw from '../../examples/acceptance-criteria.txt?raw';
import diffWithBug from '../../examples/diff-with-bug.diff?raw';
import diffFixed from '../../examples/diff-fixed.diff?raw';

/**
 * The demo ticket. The files live in examples/, at the repository root, and are the same ones
 * the backend tests read.
 */
export const TICKET = {
  id: 'SHOP-412',
  title: 'Clarify the cart amount',
  criteria: criteriaRaw.trim(),
};

export const DIFF_WITH_BUG = diffWithBug.trimEnd();
export const DIFF_FIXED = diffFixed.trimEnd();
