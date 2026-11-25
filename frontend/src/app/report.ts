/** Mirror of the backend record dev.memolabs.fec.core.ReconciliationReport. */
export type Mode = 'AUTO' | 'OPENING' | 'FULL';
export type Status = 'OK' | 'DIFFERENCE' | 'MISSING_IN_NEW' | 'MISSING_IN_OLD';

export interface FileSummary {
  name: string;
  encoding: string;
  separator: string;
  entryLines: number;
  comparedLines: number;
  totalDebit: number;
  totalCredit: number;
  balanced: boolean;
}

export interface Line {
  key: string;
  label: string;
  oldAccounts: string[];
  oldBalance: number;
  newBalance: number;
  difference: number;
  status: Status;
}

export interface Report {
  mode: Mode;
  balanceSheetOnly: boolean;
  openingJournals: string[];
  verdict: 'MATCH' | 'DIFFERENCES';
  oldFile: FileSummary;
  newFile: FileSummary;
  accounts: Line[];
  thirdParties: Line[];
  warnings: string[];
}

export interface ReconcileRequest {
  oldFec: File;
  newFec: File;
  mapping: File | null;
  mode: Mode;
  openingJournals: string;
  balanceSheetOnly: boolean;
}

export const STATUS_LABELS: Record<Status, string> = {
  OK: 'Conforme',
  DIFFERENCE: 'Écart',
  MISSING_IN_NEW: 'Absent du nouveau',
  MISSING_IN_OLD: "Absent de l'ancien",
};
