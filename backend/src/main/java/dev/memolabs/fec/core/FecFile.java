package dev.memolabs.fec.core;

import java.math.BigDecimal;
import java.util.List;

/**
 * A parsed FEC.
 *
 * @param name     file name, as given by the user
 * @param encoding charset the file was read with
 * @param separator field separator found in the header (tab or pipe)
 * @param warnings problems that do not stop the reconciliation (an unbalanced entry, for example)
 */
public record FecFile(String name, String encoding, char separator, List<FecEntry> entries, List<String> warnings) {

    public BigDecimal totalDebit() {
        return entries.stream().map(FecEntry::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCredit() {
        return entries.stream().map(FecEntry::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
