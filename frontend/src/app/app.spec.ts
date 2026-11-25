import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { Report } from './report';

registerLocaleData(localeFr);

const REPORT: Report = {
  mode: 'OPENING',
  balanceSheetOnly: false,
  openingJournals: ['AN'],
  verdict: 'DIFFERENCES',
  oldFile: { name: 'ancien.txt', encoding: 'UTF-8', separator: '|', entryLines: 2, comparedLines: 2, totalDebit: 10, totalCredit: 10, balanced: true },
  newFile: { name: 'nouveau.txt', encoding: 'UTF-8', separator: '|', entryLines: 2, comparedLines: 2, totalDebit: 10, totalCredit: 10, balanced: true },
  accounts: [
    { key: '411100', label: 'Clients', oldAccounts: ['411000'], oldBalance: 1800, newBalance: 1200, difference: -600, status: 'DIFFERENCE' },
    { key: '512100', label: 'Banque', oldAccounts: ['512000'], oldBalance: 10, newBalance: 10, difference: 0, status: 'OK' },
  ],
  thirdParties: [],
  warnings: [],
};

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: LOCALE_ID, useValue: 'fr-FR' }],
    }).compileComponents();
  });

  it('keeps the compare button disabled until both FEC are chosen', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.primary');
    expect(button?.disabled).toBe(true);
  });

  it('shows the differences first and hides matching accounts', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance as unknown as {
      oldFec: { set(f: File): void };
      newFec: { set(f: File): void };
      compare(): void;
    };
    app.oldFec.set(new File(['x'], 'ancien.txt'));
    app.newFec.set(new File(['x'], 'nouveau.txt'));
    app.compare();

    TestBed.inject(HttpTestingController).expectOne('/api/reconcile').flush(REPORT);
    await fixture.whenStable();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('#verdict-title')?.textContent).toContain('1 compte(s)');
    const rows = page.querySelectorAll('tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('411100');
    expect(rows[0].textContent).toContain('Écart');
  });

  it('explains when the Java server is down', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance as unknown as {
      oldFec: { set(f: File): void };
      newFec: { set(f: File): void };
      compare(): void;
    };
    app.oldFec.set(new File(['x'], 'a.txt'));
    app.newFec.set(new File(['x'], 'b.txt'));
    app.compare();

    TestBed.inject(HttpTestingController)
      .expectOne('/api/reconcile')
      .error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role=alert]')?.textContent).toContain(
      'serveur Java',
    );
  });
});
