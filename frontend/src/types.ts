/** Los tres veredictos posibles. Coinciden con el enum del backend. */
export type ReviewStatus = 'MET' | 'NOT_MET' | 'UNVERIFIED';

/** Lado del diff al que apunta una cita. */
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
  explanation: string;
  evidence: Evidence | null;
  /** Por qué no se pudo verificar la cita. Null cuando todo cuadró. */
  verificationNote: string | null;
}

export interface ReviewResponse {
  results: CriterionResult[];
  model: string;
}

export const STATUS_LABEL: Record<ReviewStatus, string> = {
  MET: 'Cumplido',
  NOT_MET: 'No cumplido',
  UNVERIFIED: 'No verificado',
};

export const SIDE_LABEL: Record<DiffSide, string> = {
  ADDED: 'línea añadida',
  REMOVED: 'línea eliminada',
  CONTEXT: 'línea de contexto',
};

/** El marcador con el que esa línea aparece en el diff. */
export const SIDE_MARKER: Record<DiffSide, string> = {
  ADDED: '+',
  REMOVED: '-',
  CONTEXT: ' ',
};
