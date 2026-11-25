package dev.memolabs.fec.core;

import dev.memolabs.fec.core.ReconciliationReport.FileSummary;
import dev.memolabs.fec.core.ReconciliationReport.Line;
import dev.memolabs.fec.core.ReconciliationReport.Mode;
import dev.memolabs.fec.core.ReconciliationReport.Status;
import dev.memolabs.fec.core.ReconciliationReport.Verdict;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Compares the accounting of an old system with the new one, by balance per account and per third
 * party. Entries are never matched one by one: the new software renumbers imported entries, so
 * only balances survive a migration.
 */
public final class Reconciler {

    public static final List<String> DEFAULT_OPENING_JOURNALS = List.of("AN", "RAN", "ANO");

    private Reconciler() {
    }

    /**
     * @param balanceSheetOnly ignore classes 6 and 7 (expenses and income), for a migration at year end
     *                         where only the balance sheet is carried forward
     */
    public static ReconciliationReport compare(
            FecFile oldFec,
            FecFile newFec,
            AccountMapping mapping,
            Mode mode,
            List<String> openingJournals,
            boolean balanceSheetOnly) {
        Set<String> journals = openingJournals.stream()
                .map(j -> j.trim().toUpperCase(Locale.ROOT))
                .filter(j -> !j.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        Predicate<FecEntry> isOpening = e -> journals.contains(e.journalCode().toUpperCase(Locale.ROOT));
        Mode effective = mode;
        if (mode == Mode.AUTO) {
            effective = newFec.entries().stream().anyMatch(isOpening) ? Mode.OPENING : Mode.FULL;
        }
        Predicate<FecEntry> inScope = e -> !balanceSheetOnly || !isIncomeStatement(mapping.map(e.compteNum()));
        List<FecEntry> oldEntries = oldFec.entries().stream().filter(inScope).toList();
        Predicate<FecEntry> newFilter = effective == Mode.OPENING ? isOpening.and(inScope) : inScope;
        List<FecEntry> newEntries = newFec.entries().stream().filter(newFilter).toList();

        List<String> warnings = new ArrayList<>();
        oldFec.warnings().forEach(w -> warnings.add("Ancien FEC : " + w));
        newFec.warnings().forEach(w -> warnings.add("Nouveau FEC : " + w));
        if (mode == Mode.OPENING && newEntries.isEmpty()) {
            warnings.add("Aucune écriture d'à-nouveaux trouvée dans le nouveau FEC (journaux "
                    + String.join(", ", journals) + ").");
        }
        if (!mapping.isEmpty()) {
            Set<String> unmapped = oldEntries.stream()
                    .map(FecEntry::compteNum)
                    .filter(a -> !mapping.isMapped(a))
                    .collect(Collectors.toCollection(TreeSet::new));
            if (!unmapped.isEmpty()) {
                warnings.add(unmapped.size() + " compte(s) de l'ancien FEC absents de la table de correspondance,"
                        + " gardés tels quels : " + preview(unmapped));
            }
        }

        List<Line> accounts = compareBy(oldEntries, newEntries, e -> mapping.map(e.compteNum()),
                FecEntry::compteNum, FecEntry::compteLib);
        List<FecEntry> oldAux = oldEntries.stream().filter(FecEntry::hasAuxiliary).toList();
        List<FecEntry> newAux = newEntries.stream().filter(FecEntry::hasAuxiliary).toList();
        List<Line> thirdParties = compareBy(oldAux, newAux, FecEntry::compAuxNum, FecEntry::compAuxNum,
                FecEntry::compAuxLib);

        boolean match = accounts.stream().allMatch(l -> l.status() == Status.OK)
                && thirdParties.stream().allMatch(l -> l.status() == Status.OK);
        return new ReconciliationReport(
                effective,
                balanceSheetOnly,
                List.copyOf(journals),
                match ? Verdict.MATCH : Verdict.DIFFERENCES,
                summary(oldFec, oldEntries.size()),
                summary(newFec, newEntries.size()),
                accounts,
                thirdParties,
                List.copyOf(warnings));
    }

    /** Classes 6 (expenses) and 7 (income) of the French chart of accounts. */
    private static boolean isIncomeStatement(String account) {
        return account.startsWith("6") || account.startsWith("7");
    }

    /**
     * @param oldKey  key of an old entry in the new numbering (after mapping)
     * @param rawKey  key as written in the file: the old number for the correspondence table, and
     *                the key of a new entry, which is already in the new numbering
     */
    private static List<Line> compareBy(
            List<FecEntry> oldEntries,
            List<FecEntry> newEntries,
            Function<FecEntry, String> oldKey,
            Function<FecEntry, String> rawKey,
            Function<FecEntry, String> label) {
        Map<String, BigDecimal> oldBalances = new TreeMap<>();
        Map<String, Set<String>> sources = new TreeMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (FecEntry e : oldEntries) {
            String key = oldKey.apply(e);
            oldBalances.merge(key, e.signedAmount(), BigDecimal::add);
            sources.computeIfAbsent(key, k -> new TreeSet<>()).add(rawKey.apply(e));
            labels.putIfAbsent(key, label.apply(e));
        }
        Map<String, BigDecimal> newBalances = new TreeMap<>();
        for (FecEntry e : newEntries) {
            String key = rawKey.apply(e);
            newBalances.merge(key, e.signedAmount(), BigDecimal::add);
            String newLabel = label.apply(e);
            if (!newLabel.isBlank()) {
                labels.put(key, newLabel);
            }
        }
        Set<String> keys = new TreeSet<>(oldBalances.keySet());
        keys.addAll(newBalances.keySet());
        List<Line> lines = new ArrayList<>();
        for (String key : keys) {
            BigDecimal oldBalance = oldBalances.get(key);
            BigDecimal newBalance = newBalances.get(key);
            BigDecimal oldValue = oldBalance == null ? BigDecimal.ZERO : oldBalance;
            BigDecimal newValue = newBalance == null ? BigDecimal.ZERO : newBalance;
            BigDecimal difference = newValue.subtract(oldValue);
            Status status;
            if (difference.signum() == 0) {
                status = Status.OK;
            } else if (newBalance == null) {
                status = Status.MISSING_IN_NEW;
            } else if (oldBalance == null) {
                status = Status.MISSING_IN_OLD;
            } else {
                status = Status.DIFFERENCE;
            }
            lines.add(new Line(key, labels.getOrDefault(key, ""), List.copyOf(sources.getOrDefault(key, Set.of())),
                    oldValue, newValue, difference, status));
        }
        lines.sort(Comparator.comparing((Line l) -> l.status() == Status.OK).thenComparing(Line::key));
        return List.copyOf(lines);
    }

    private static FileSummary summary(FecFile file, int comparedLines) {
        BigDecimal debit = file.totalDebit();
        BigDecimal credit = file.totalCredit();
        String separator = file.separator() == '\t' ? "tabulation" : "|";
        return new FileSummary(file.name(), file.encoding(), separator, file.entries().size(), comparedLines,
                debit, credit, debit.compareTo(credit) == 0);
    }

    private static String preview(Set<String> values) {
        List<String> first = values.stream().limit(8).toList();
        String more = values.size() > first.size() ? ", …" : "";
        return String.join(", ", first) + more;
    }
}
