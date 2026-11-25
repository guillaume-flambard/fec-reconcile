package dev.memolabs.fec.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.memolabs.fec.core.ReconciliationReport.Line;
import dev.memolabs.fec.core.ReconciliationReport.Mode;
import dev.memolabs.fec.core.ReconciliationReport.Status;
import dev.memolabs.fec.core.ReconciliationReport.Verdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs on the fictional files of samples/, written by samples/make_samples.py. */
class ReconcilerTest {

    private static final Path SAMPLES = Path.of("..", "samples");

    private static FecFile oldFec() throws IOException {
        return FecParser.parse("ancien", Files.readAllBytes(SAMPLES.resolve("000000000FEC20260630-ancien.txt")));
    }

    private static FecFile newFec() throws IOException {
        return FecParser.parse("nouveau", Files.readAllBytes(SAMPLES.resolve("000000000FEC20261231-nouveau.txt")));
    }

    private static AccountMapping mapping() throws IOException {
        return AccountMapping.parse(Files.readAllBytes(SAMPLES.resolve("correspondance-comptes.csv")));
    }

    private static Line line(List<Line> lines, String key) {
        return lines.stream().filter(l -> l.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void findsTheForgottenInvoiceAndTheSuspenseAccount() throws IOException {
        ReconciliationReport report = Reconciler.compare(oldFec(), newFec(), mapping(), Mode.AUTO,
                Reconciler.DEFAULT_OPENING_JOURNALS, false);

        assertThat(report.mode()).isEqualTo(Mode.OPENING);
        assertThat(report.verdict()).isEqualTo(Verdict.DIFFERENCES);
        assertThat(report.accountDifferences()).isEqualTo(2);

        Line customers = line(report.accounts(), "411100");
        assertThat(customers.status()).isEqualTo(Status.DIFFERENCE);
        assertThat(customers.oldAccounts()).containsExactly("411000");
        assertThat(customers.oldBalance()).isEqualByComparingTo("1800");
        assertThat(customers.newBalance()).isEqualByComparingTo("1200");
        assertThat(customers.difference()).isEqualByComparingTo("-600");

        Line suspense = line(report.accounts(), "471000");
        assertThat(suspense.status()).isEqualTo(Status.MISSING_IN_OLD);
        assertThat(suspense.difference()).isEqualByComparingTo("600");

        Line garage = line(report.thirdParties(), "C002");
        assertThat(garage.status()).isEqualTo(Status.MISSING_IN_NEW);
        assertThat(garage.label()).isEqualTo("Garage Martin");
    }

    @Test
    void ignoresTheEntriesAfterTheCutoverWhenComparingOpeningBalances() throws IOException {
        ReconciliationReport report = Reconciler.compare(oldFec(), newFec(), mapping(), Mode.OPENING,
                Reconciler.DEFAULT_OPENING_JOURNALS, false);

        assertThat(line(report.accounts(), "706100").status()).isEqualTo(Status.OK);
        assertThat(report.newFile().entryLines()).isEqualTo(12);
        assertThat(report.newFile().comparedLines()).isEqualTo(9);
    }

    @Test
    void putsTheDifferencesFirst() throws IOException {
        ReconciliationReport report = Reconciler.compare(oldFec(), newFec(), mapping(), Mode.AUTO,
                Reconciler.DEFAULT_OPENING_JOURNALS, false);

        assertThat(report.accounts().subList(0, 2)).extracting(Line::key).containsExactly("411100", "471000");
    }

    @Test
    void withoutTheMappingEveryAccountLooksMissing() throws IOException {
        ReconciliationReport report = Reconciler.compare(oldFec(), newFec(), AccountMapping.identity(), Mode.AUTO,
                Reconciler.DEFAULT_OPENING_JOURNALS, false);

        assertThat(line(report.accounts(), "512000").status()).isEqualTo(Status.MISSING_IN_NEW);
        assertThat(line(report.accounts(), "512100").status()).isEqualTo(Status.MISSING_IN_OLD);
        assertThat(report.warnings().getFirst()).contains("table de correspondance");
    }

    @Test
    void warnsAboutOldAccountsMissingFromTheMapping() throws IOException {
        AccountMapping partial = AccountMapping.parse("ancien;nouveau\n512000;512100\n".getBytes(StandardCharsets.UTF_8));

        ReconciliationReport report = Reconciler.compare(oldFec(), newFec(), partial, Mode.AUTO,
                Reconciler.DEFAULT_OPENING_JOURNALS, false);

        assertThat(report.warnings()).anySatisfy(w -> assertThat(w).contains("absents de la table").contains("411000"));
    }

    @Test
    void canLeaveOutExpensesAndIncome() throws IOException {
        ReconciliationReport report = Reconciler.compare(oldFec(), newFec(), mapping(), Mode.AUTO,
                Reconciler.DEFAULT_OPENING_JOURNALS, true);

        assertThat(report.accounts()).extracting(Line::key).doesNotContain("706100", "607100");
    }

    @Test
    void matchesWhenTheMigrationIsRight() {
        String header = "JournalCode|EcritureNum|EcritureDate|CompteNum|CompAuxNum|Debit|Credit\n";
        FecFile old = FecParser.parse("a", (header + "VE|1|20260101|411000|C1|50,00|0\nVE|1|20260101|706000||0|50,00\n")
                .getBytes(StandardCharsets.UTF_8));
        FecFile renumbered = FecParser.parse("b", (header + "AN|900|20260701|411000|C1|50,00|0\nAN|900|20260701|706000||0|50,00\n")
                .getBytes(StandardCharsets.UTF_8));

        ReconciliationReport report = Reconciler.compare(old, renumbered, AccountMapping.identity(), Mode.AUTO,
                Reconciler.DEFAULT_OPENING_JOURNALS, false);

        assertThat(report.verdict()).isEqualTo(Verdict.MATCH);
    }

    @Test
    void writesTheCorrespondenceTableAsCsv() throws IOException {
        ReconciliationReport report = Reconciler.compare(oldFec(), newFec(), mapping(), Mode.AUTO,
                Reconciler.DEFAULT_OPENING_JOURNALS, false);

        String csv = ReconciliationCsv.toCsv(report);

        assertThat(csv).startsWith("\uFEFFType;Nouveau compte ou tiers");
        assertThat(csv).contains("Compte;411100;Clients France;411000;1800,00;1200,00;-600,00;Écart");
        assertThat(csv).contains("Tiers;C002;Garage Martin;C002;600,00;0,00;-600,00;Absent du nouveau système");
    }
}
