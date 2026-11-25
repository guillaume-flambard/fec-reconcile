"""Writes the fictional sample files used by the tests and the demo.

Ateliers Dubois SARL (SIREN 000000000, fictional) leaves its old software on 30 June 2026.
The old FEC is tab separated and ISO 8859-15, the new one pipe separated and UTF-8, as two
different vendors would produce. The opening entries of the new system contain two realistic
mistakes: the open invoice of customer C002 was forgotten, and its 600 EUR went to a suspense
account (471000).

Run: python3 samples/make_samples.py
"""

from pathlib import Path

HEADER = [
    "JournalCode", "JournalLib", "EcritureNum", "EcritureDate", "CompteNum", "CompteLib",
    "CompAuxNum", "CompAuxLib", "PieceRef", "PieceDate", "EcritureLib", "Debit", "Credit",
    "EcritureLet", "DateLet", "ValidDate", "Montantdevise", "Idevise",
]

# journal, journal label, number, date, account, label, aux, aux label, piece, text, debit, credit
OLD = [
    ("AN", "À-nouveaux", "1", "20260101", "512000", "Banque", "", "", "AN26", "Reprise", "15000,00", "0,00"),
    ("AN", "À-nouveaux", "1", "20260101", "101000", "Capital", "", "", "AN26", "Reprise", "0,00", "10000,00"),
    ("AN", "À-nouveaux", "1", "20260101", "401000", "Fournisseurs", "F001", "Bois du Jura", "AN26", "Reprise", "0,00", "5000,00"),
    ("VE", "Ventes", "2", "20260215", "411000", "Clients", "C001", "Mairie de Lons", "FA101", "Façade mairie", "1200,00", "0,00"),
    ("VE", "Ventes", "2", "20260215", "706000", "Prestations", "", "", "FA101", "Façade mairie", "0,00", "1000,00"),
    ("VE", "Ventes", "2", "20260215", "445710", "TVA collectée", "", "", "FA101", "Façade mairie", "0,00", "200,00"),
    ("AC", "Achats", "3", "20260310", "607000", "Achats", "", "", "BJ55", "Chêne", "800,00", "0,00"),
    ("AC", "Achats", "3", "20260310", "445660", "TVA déductible", "", "", "BJ55", "Chêne", "160,00", "0,00"),
    ("AC", "Achats", "3", "20260310", "401000", "Fournisseurs", "F001", "Bois du Jura", "BJ55", "Chêne", "0,00", "960,00"),
    ("BQ", "Banque", "4", "20260420", "512000", "Banque", "", "", "VIR12", "Règlement BJ55", "0,00", "960,00"),
    ("BQ", "Banque", "4", "20260420", "401000", "Fournisseurs", "F001", "Bois du Jura", "VIR12", "Règlement BJ55", "960,00", "0,00"),
    ("VE", "Ventes", "5", "20260505", "411000", "Clients", "C002", "Garage Martin", "FA102", "Portail garage", "600,00", "0,00"),
    ("VE", "Ventes", "5", "20260505", "706000", "Prestations", "", "", "FA102", "Portail garage", "0,00", "500,00"),
    ("VE", "Ventes", "5", "20260505", "445710", "TVA collectée", "", "", "FA102", "Portail garage", "0,00", "100,00"),
]

NEW = [
    ("AN", "Reprise", "1", "20260701", "512100", "Banque principale", "", "", "REPRISE", "Reprise au 30/06", "14040,00", "0,00"),
    ("AN", "Reprise", "1", "20260701", "101300", "Capital souscrit appelé versé", "", "", "REPRISE", "Reprise au 30/06", "0,00", "10000,00"),
    ("AN", "Reprise", "1", "20260701", "401100", "Fournisseurs France", "F001", "Bois du Jura", "REPRISE", "Reprise au 30/06", "0,00", "5000,00"),
    ("AN", "Reprise", "1", "20260701", "411100", "Clients France", "C001", "Mairie de Lons", "REPRISE", "Reprise au 30/06", "1200,00", "0,00"),
    ("AN", "Reprise", "1", "20260701", "471000", "Compte d'attente", "", "", "REPRISE", "Reprise au 30/06", "600,00", "0,00"),
    ("AN", "Reprise", "1", "20260701", "706100", "Prestations de services", "", "", "REPRISE", "Reprise au 30/06", "0,00", "1500,00"),
    ("AN", "Reprise", "1", "20260701", "445711", "TVA collectée 20 %", "", "", "REPRISE", "Reprise au 30/06", "0,00", "300,00"),
    ("AN", "Reprise", "1", "20260701", "607100", "Achats de matières", "", "", "REPRISE", "Reprise au 30/06", "800,00", "0,00"),
    ("AN", "Reprise", "1", "20260701", "445661", "TVA déductible 20 %", "", "", "REPRISE", "Reprise au 30/06", "160,00", "0,00"),
    # A normal entry after the cutover: ignored when only the opening entries are compared.
    ("VT", "Ventes", "2", "20260710", "411100", "Clients France", "C001", "Mairie de Lons", "FV2026-001", "Escalier", "2400,00", "0,00"),
    ("VT", "Ventes", "2", "20260710", "706100", "Prestations de services", "", "", "FV2026-001", "Escalier", "0,00", "2000,00"),
    ("VT", "Ventes", "2", "20260710", "445711", "TVA collectée 20 %", "", "", "FV2026-001", "Escalier", "0,00", "400,00"),
]

MAPPING = [
    ("512000", "512100"), ("101000", "101300"), ("401000", "401100"), ("411000", "411100"),
    ("706000", "706100"), ("445710", "445711"), ("607000", "607100"), ("445660", "445661"),
]


def rows(entries):
    for j, jl, num, date, acc, lib, aux, auxlib, piece, text, debit, credit in entries:
        yield [j, jl, num, date, acc, lib, aux, auxlib, piece, date, text, debit, credit, "", "", date, "", ""]


def main():
    here = Path(__file__).parent
    old = "\r\n".join("\t".join(r) for r in [HEADER, *rows(OLD)]) + "\r\n"
    (here / "000000000FEC20260630-ancien.txt").write_bytes(old.encode("iso-8859-15"))
    new = "\n".join("|".join(r) for r in [HEADER, *rows(NEW)]) + "\n"
    (here / "000000000FEC20261231-nouveau.txt").write_text(new, encoding="utf-8")
    mapping = "ancien;nouveau\n" + "\n".join(f"{a};{b}" for a, b in MAPPING) + "\n"
    (here / "correspondance-comptes.csv").write_text(mapping, encoding="utf-8")


if __name__ == "__main__":
    main()
