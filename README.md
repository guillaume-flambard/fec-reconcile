# fec-reconcile

Compare the accounting of an old system with the new one before an ERP cutover, from the two
FEC files (fichier des écritures comptables, the export every French accounting software must
produce). The tool lists every account and every third party whose balance differs, and writes
the correspondence and reconciliation table that the tax administration asks for when a change
of software also changes the chart of accounts.

Status: prototype, 25 November 2026. Not yet tested on a real company's files.

## Why

When a company changes software during a financial year and changes its chart of accounts,
BOFiP [BOI-CF-IOR-60-40-20 § 400](https://bofip.impots.gouv.fr/bofip/9028-PGP.html/identifiant=BOI-CF-IOR-60-40-20-20170607)
asks for two FEC and "tables de correspondances et de réconciliation comptable" between the two
systems. Existing open source FEC tools check one file at a time; this one compares two.

Entries are never matched one by one: the new software renumbers imported entries, so the
comparison works on balances per account and per third party.

## Run it

Requirements: Java 17 or later, Node 22.22 or later.

```bash
cd backend && ./mvnw spring-boot:run      # API on http://127.0.0.1:8080
cd frontend && npm install && npm start   # screen on http://localhost:4200
```

Open the screen and click "Essayer avec l'exemple" to run the fictional sample in `samples/`.

The API listens on 127.0.0.1 only. Files are read in memory and never written to disk or logged.

## What it compares

- **Opening mode** (default when the new FEC has AN, RAN or ANO entries): the closing balance of
  the old FEC against the opening entries of the new one.
- **Full mode**: both files hold the same history and are compared as a whole.
- An optional `old;new` CSV maps the old accounts to the new chart. Third parties are compared
  on their code, without mapping.
- Classes 6 and 7 can be left out for a cutover at year end.

Accepted formats follow article A47 A-1 of the LPF: tab or pipe separated, UTF-8 or ISO 8859-15,
dates as AAAAMMJJ, decimal comma, Debit and Credit or Montant and Sens.

## Checks

```bash
cd backend && ./mvnw test
cd frontend && npx ng lint && npx ng test --watch=false && npx ng build
```

## Stack

Java 17, Spring Boot 4.2, Maven; Angular 22 (standalone, zoneless, signals), Vitest, angular-eslint.

## Licence

MIT, see `LICENSE`. The agent skills in `.claude/skills/` keep their own licences, listed in
`.claude/skills/SOURCES.md`. The sample files describe a fictional company.
