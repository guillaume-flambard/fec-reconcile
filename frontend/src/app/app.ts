import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { BalanceTable } from './balance-table';
import { FileDrop } from './file-drop';
import { findings } from './finding';
import { ReconcileApi } from './reconcile-api';
import { Mode, ReconcileRequest, Report } from './report';

type State = 'waiting' | 'loading' | 'done' | 'error';

const SAMPLE_FILES = {
  oldFec: '000000000FEC20260630-ancien.txt',
  newFec: '000000000FEC20261231-nouveau.txt',
  mapping: 'correspondance-comptes.csv',
};

@Component({
  imports: [DecimalPipe, BalanceTable, FileDrop],
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

  /** True while the screen shows the fictional example rather than the user's files. */
  protected readonly isExample = signal(false);
  protected readonly state = signal<State>('waiting');
  protected readonly report = signal<Report | null>(null);
  protected readonly error = signal('');

  protected readonly accountFindings = computed(() => findings(this.report()?.accounts ?? [], 'account'));
  protected readonly thirdPartyFindings = computed(() =>
    findings(this.report()?.thirdParties ?? [], 'thirdParty'),
  );
  protected readonly differenceCount = computed(
    () => this.accountFindings().length + this.thirdPartyFindings().length,
  );
  protected readonly matchingAccounts = computed(
    () => (this.report()?.accounts ?? []).filter((l) => l.status === 'OK').length,
  );
  protected readonly matchingThirdParties = computed(
    () => (this.report()?.thirdParties ?? []).filter((l) => l.status === 'OK').length,
  );
  protected readonly verdict = computed(() => {
    const n = this.differenceCount();
    if (n === 0) {
      return 'Tout a été repris.';
    }
    return n === 1 ? '1 différence à expliquer.' : `${n} différences à expliquer.`;
  });
  protected readonly waitingFor = computed(() => {
    if (!this.oldFec() && !this.newFec()) {
      return 'Déposez les deux fichiers pour lancer la comparaison.';
    }
    return this.oldFec()
      ? 'Déposez aussi le fichier « après » pour lancer la comparaison.'
      : 'Déposez aussi le fichier « avant » pour lancer la comparaison.';
  });

  constructor() {
    void this.loadSample();
  }

  /** The user's own file replaces the example: the other example files go too. */
  protected onPicked(): void {
    if (this.isExample()) {
      this.isExample.set(false);
      this.report.set(null);
      if (this.oldFec()?.name === SAMPLE_FILES.oldFec) this.oldFec.set(null);
      if (this.newFec()?.name === SAMPLE_FILES.newFec) this.newFec.set(null);
      if (this.mapping()?.name === SAMPLE_FILES.mapping) this.mapping.set(null);
    }
    if (this.oldFec() && this.newFec()) {
      this.compare();
    } else {
      this.state.set('waiting');
    }
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

  protected async loadSample(): Promise<void> {
    try {
      const [oldFec, newFec, mapping] = await Promise.all(
        Object.values(SAMPLE_FILES).map(async (name) => {
          const response = await fetch(`exemple/${name}`);
          return new File([await response.blob()], name, { type: 'text/plain' });
        }),
      );
      // The user dropped a file while the example was loading: theirs wins.
      if (this.oldFec() || this.newFec()) {
        return;
      }
      this.oldFec.set(oldFec);
      this.newFec.set(newFec);
      this.mapping.set(mapping);
      this.isExample.set(true);
      this.compare();
    } catch {
      // No example available: the screen simply waits for the user's files.
    }
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
        link.download = 'rapport-de-bascule.csv';
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (e: HttpErrorResponse) => {
        this.error.set(errorMessage(e));
        this.state.set('error');
      },
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
}

function errorMessage(e: HttpErrorResponse): string {
  if (e.status === 0 || e.status === 502 || e.status === 504) {
    return "Le moteur de comparaison ne répond pas. Lancez-le avec « ./mvnw spring-boot:run » dans le dossier backend, puis déposez à nouveau un fichier.";
  }
  const detail = (e.error as { detail?: string } | null)?.detail;
  return detail ?? `Le serveur a répondu par une erreur ${e.status}.`;
}
