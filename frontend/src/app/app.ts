import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal, WritableSignal } from '@angular/core';
import { BalanceTable } from './balance-table';
import { ReconcileApi } from './reconcile-api';
import { Line, Mode, ReconcileRequest, Report } from './report';

type State = 'idle' | 'loading' | 'done' | 'error';

const SAMPLE_FILES = {
  oldFec: '000000000FEC20260630-ancien.txt',
  newFec: '000000000FEC20261231-nouveau.txt',
  mapping: 'correspondance-comptes.csv',
};

@Component({
  imports: [DecimalPipe, BalanceTable],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {
  private readonly api = inject(ReconcileApi);

  protected readonly oldFec = signal<File | null>(null);
  protected readonly newFec = signal<File | null>(null);
  protected readonly mapping = signal<File | null>(null);
  protected readonly mode = signal<Mode>('AUTO');
  protected readonly openingJournals = signal('AN,RAN,ANO');
  protected readonly balanceSheetOnly = signal(false);
  protected readonly onlyDifferences = signal(true);

  protected readonly state = signal<State>('idle');
  protected readonly report = signal<Report | null>(null);
  protected readonly error = signal('');

  protected readonly canCompare = computed(
    () => this.oldFec() !== null && this.newFec() !== null && this.state() !== 'loading',
  );
  protected readonly visibleAccounts = computed(() => this.filter(this.report()?.accounts ?? []));
  protected readonly visibleThirdParties = computed(() => this.filter(this.report()?.thirdParties ?? []));
  protected readonly accountDifferences = computed(() => countDifferences(this.report()?.accounts ?? []));
  protected readonly thirdPartyDifferences = computed(() => countDifferences(this.report()?.thirdParties ?? []));
  protected readonly modeLabel = computed(() => {
    const r = this.report();
    if (!r) {
      return '';
    }
    const compared =
      r.mode === 'OPENING'
        ? `Solde de l'ancien contre les à-nouveaux du nouveau (journaux ${r.openingJournals.join(', ')})`
        : 'Les deux fichiers comparés en entier';
    return r.balanceSheetOnly ? `${compared}, classes 6 et 7 ignorées.` : `${compared}.`;
  });

  protected pick(event: Event, target: WritableSignal<File | null>): void {
    const input = event.target as HTMLInputElement;
    target.set(input.files?.[0] ?? null);
  }

  protected setMode(event: Event): void {
    this.mode.set((event.target as HTMLInputElement).value as Mode);
  }

  protected setJournals(event: Event): void {
    this.openingJournals.set((event.target as HTMLInputElement).value);
  }

  protected setBalanceSheetOnly(event: Event): void {
    this.balanceSheetOnly.set((event.target as HTMLInputElement).checked);
  }

  protected setOnlyDifferences(event: Event): void {
    this.onlyDifferences.set((event.target as HTMLInputElement).checked);
  }

  protected async loadSample(): Promise<void> {
    const [oldFec, newFec, mapping] = await Promise.all(
      Object.values(SAMPLE_FILES).map(async (name) => {
        const response = await fetch(`exemple/${name}`);
        return new File([await response.blob()], name, { type: 'text/plain' });
      }),
    );
    this.oldFec.set(oldFec);
    this.newFec.set(newFec);
    this.mapping.set(mapping);
    this.compare();
  }

  protected compare(): void {
    const request = this.request();
    if (!request) {
      return;
    }
    this.state.set('loading');
    this.error.set('');
    this.api.compare(request).subscribe({
      next: (report) => {
        this.report.set(report);
        this.state.set('done');
      },
      error: (e: HttpErrorResponse) => {
        this.report.set(null);
        this.error.set(errorMessage(e));
        this.state.set('error');
      },
    });
  }

  protected downloadCsv(): void {
    const request = this.request();
    if (!request) {
      return;
    }
    this.api.csv(request).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'table-de-correspondance-et-reconciliation.csv';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (e: HttpErrorResponse) => this.error.set(errorMessage(e)),
    });
  }

  private request(): ReconcileRequest | null {
    const oldFec = this.oldFec();
    const newFec = this.newFec();
    if (!oldFec || !newFec) {
      return null;
    }
    return {
      oldFec,
      newFec,
      mapping: this.mapping(),
      mode: this.mode(),
      openingJournals: this.openingJournals(),
      balanceSheetOnly: this.balanceSheetOnly(),
    };
  }

  private filter(lines: Line[]): Line[] {
    return this.onlyDifferences() ? lines.filter((l) => l.status !== 'OK') : lines;
  }
}

function countDifferences(lines: Line[]): number {
  return lines.filter((l) => l.status !== 'OK').length;
}

function errorMessage(e: HttpErrorResponse): string {
  if (e.status === 0 || e.status === 502 || e.status === 504) {
    return 'Le serveur Java ne répond pas. Lancez-le avec « ./mvnw spring-boot:run » dans le dossier backend.';
  }
  const detail = (e.error as { detail?: string } | null)?.detail;
  return detail ?? `Erreur ${e.status} du serveur.`;
}
