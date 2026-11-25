package dev.memolabs.fec.core;

import java.math.BigDecimal;
import java.util.List;

/** Everything the comparison found, ready to be shown or exported. */
public record ReconciliationReport(
        Mode mode,
        boolean balanceSheetOnly,
        List<String> openingJournals,
        Verdict verdict,
        FileSummary oldFile,
        FileSummary newFile,
        List<Line> accounts,
        List<Line> thirdParties,
        List<String> warnings) {

    /**
     * OPENING: the old closing balance against the opening entries (à-nouveaux) of the new file.
     * FULL: both files hold the same history and are compared as a whole.
     */
    public enum Mode { AUTO, OPENING, FULL }

    public enum Verdict { MATCH, DIFFERENCES }

    public enum Status { OK, DIFFERENCE, MISSING_IN_NEW, MISSING_IN_OLD }

    public record FileSummary(
            String name,
            String encoding,
            String separator,
            int entryLines,
            int comparedLines,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced) {
    }

    /**
     * One account (or third party) after mapping.
     *
     * @param key         account number in the new chart, or third-party code
     * @param oldAccounts old account numbers that fold into this one (the correspondence table)
     * @param difference  new balance minus old balance
     */
    public record Line(
            String key,
            String label,
            List<String> oldAccounts,
            BigDecimal oldBalance,
            BigDecimal newBalance,
            BigDecimal difference,
            Status status) {
    }

    public long accountDifferences() {
        return accounts.stream().filter(l -> l.status() != Status.OK).count();
    }

    public long thirdPartyDifferences() {
        return thirdParties.stream().filter(l -> l.status() != Status.OK).count();
    }
}
