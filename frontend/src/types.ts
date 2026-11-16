/** The three possible verdicts. These match the backend enum. */
export type ReviewStatus = 'MET' | 'NOT_MET' | 'NOT_VERIFIED';

/** The side of the diff a citation points at. */
export type DiffSide = 'ADDED' | 'REMOVED' | 'CONTEXT';

export interface Evidence {
  file: string;
  side: DiffSide;
  lineNumber: number;
  snippet: string;
}

export interface CriterionResult {
  criterion: string;
  status: ReviewStatus;
  /** Null when the model never returned a verdict for this criterion. */
  explanation: string | null;
  /** The model's citation, or null if it cited nothing. */
  evidence: Evidence | null;
  /** Why the citation could not be verified. Null when it held up. */
  verificationNote: string | null;
}

export interface ReviewResponse {
  results: CriterionResult[];
  model: string;
  /** Who answered the request: "Anthropic API", or whatever stood in for it. */
  provider: string;
  /** false when the answer did not come from the Anthropic API. */
  officialProvider: boolean;
}

export const STATUS_LABEL: Record<ReviewStatus, string> = {
  MET: 'Met',
  NOT_MET: 'Not met',
  NOT_VERIFIED: 'Not verified',
};

export const SIDE_LABEL: Record<DiffSide, string> = {
  ADDED: 'added line',
  REMOVED: 'removed line',
  CONTEXT: 'context line',
};

/** The marker this line carries in the diff. */
export const SIDE_MARKER: Record<DiffSide, string> = {
  ADDED: '+',
  REMOVED: '-',
  CONTEXT: ' ',
};
