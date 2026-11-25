package dev.memolabs.fec.core;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of a FEC (fichier des écritures comptables), as defined by article A47 A-1 of the
 * Livre des procédures fiscales. Only the fields the reconciliation needs are kept.
 *
 * @param lineNumber line in the source file (1 is the header), for error messages
 */
public record FecEntry(
        int lineNumber,
        String journalCode,
        String ecritureNum,
        LocalDate ecritureDate,
        String compteNum,
        String compteLib,
        String compAuxNum,
        String compAuxLib,
        String pieceRef,
        String ecritureLib,
        BigDecimal debit,
        BigDecimal credit,
        String ecritureLet) {

    /** Debit minus credit: positive for a debit balance. */
    public BigDecimal signedAmount() {
        return debit.subtract(credit);
    }

    public boolean hasAuxiliary() {
        return compAuxNum != null && !compAuxNum.isBlank();
    }
}
