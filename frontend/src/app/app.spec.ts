import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import localeFr from '@angular/common/locales/fr';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { findings } from './finding';
import { Line, Report } from './report';

registerLocaleData(localeFr);

const CUSTOMERS: Line = {
  key: '411100',
  label: 'Clients France',
  oldAccounts: ['411000'],
  oldBalance: 1800,
  newBalance: 1200,
  difference: -600,
  status: 'DIFFERENCE',
};

const REPORT: Report = {
  mode: 'OPENING',
  balanceSheetOnly: false,
  openingJournals: ['AN'],
  verdict: 'DIFFERENCES',
  oldFile: { name: 'a.txt', encoding: 'UTF-8', separator: '|', entryLines: 2, comparedLines: 2, totalDebit: 10, totalCredit: 10, balanced: true },
  newFile: { name: 'b.txt', encoding: 'UTF-8', separator: '|', entryLines: 2, comparedLines: 2, totalDebit: 10, totalCredit: 10, balanced: true },
  accounts: [
    CUSTOMERS,
    { key: '512100', label: 'Banque', oldAccounts: ['512000'], oldBalance: 10, newBalance: 10, difference: 0, status: 'OK' },
  ],
  thirdParties: [
    { key: 'C002', label: 'Garage Martin', oldAccounts: ['C002'], oldBalance: 600, newBalance: 0, difference: -600, status: 'MISSING_IN_NEW' },
  ],
  warnings: [],
};

interface AppHandle {
  oldFec: { set(f: File): void };
  newFec: { set(f: File): void };
  compare(): void;
}

describe('App', () => {
  beforeEach(async () => {
    // No sample files in the test runner: the example fails to load and the screen waits.
    globalThis.fetch = () => Promise.reject(new Error('offline'));
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: LOCALE_ID, useValue: 'fr-FR' }],
    }).compileComponents();
  });

  it('asks for the two files when the example cannot load', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('#result-title')?.textContent).toContain(
      'En attente des fichiers',
    );
  });

  it('tells each difference in a sentence', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance as unknown as AppHandle;
    app.oldFec.set(new File(['x'], 'a.txt'));
    app.newFec.set(new File(['x'], 'b.txt'));
    app.compare();

    TestBed.inject(HttpTestingController).expectOne('/api/reconcile').flush(REPORT);
    await fixture.whenStable();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('#result-title')?.textContent).toContain('2 différences à expliquer.');
    const items = [...page.querySelectorAll('ol li')].map((li) => li.textContent ?? '');
    expect(items).toHaveLength(2);
    expect(items[0]).toContain('Clients France (compte 411100, ancien 411000)');
    expect(items[1]).toContain('Garage Martin');
    expect(items[1]).toContain('introuvable dans le nouveau');
  });

  it('explains when the comparison engine is down', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance as unknown as AppHandle;
    app.oldFec.set(new File(['x'], 'a.txt'));
    app.newFec.set(new File(['x'], 'b.txt'));
    app.compare();

    TestBed.inject(HttpTestingController)
      .expectOne('/api/reconcile')
      .error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain(
      'ne répond pas',
    );
  });
});

describe('findings', () => {
  it('leaves out matching lines and words the amounts in euros', () => {
    const [only] = findings(REPORT.accounts, 'account');
    expect(findings(REPORT.accounts, 'account')).toHaveLength(1);
    expect(only.status).toBe('Montant différent');
    expect(only.sentence).toMatch(/1\s800,00\s€ avant la bascule, 1\s200,00\s€ après : écart de 600,00\s€\./);
  });

  it('flags a suspense account', () => {
    const [suspense] = findings(
      [{ key: '471000', label: "Compte d'attente", oldAccounts: [], oldBalance: 0, newBalance: 600, difference: 600, status: 'MISSING_IN_OLD' }],
      'account',
    );
    expect(suspense.kind).toBe('extra');
    expect(suspense.hint).toContain("compte d'attente");
  });
});
