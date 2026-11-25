package dev.memolabs.fec.core;

import dev.memolabs.fec.core.ReconciliationReport.Line;
import dev.memolabs.fec.core.ReconciliationReport.Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The "table de correspondances et de réconciliation comptable" asked by BOFiP BOI-CF-IOR-60-40-20
 * § 400 when a change of software also changes the chart of accounts. Semicolon separated, decimal
 * comma and a byte order mark, so that Excel opens it correctly in French.
 */
public final class ReconciliationCsv {

    private static final Map<Status, String> STATUS_LABELS = Map.of(
            Status.OK, "Conforme",
            Status.DIFFERENCE, "Écart",
            Status.MISSING_IN_NEW, "Absent du nouveau système",
            Status.MISSING_IN_OLD, "Absent de l'ancien système");

    private ReconciliationCsv() {
    }

    public static String toCsv(ReconciliationReport report) {
        StringBuilder out = new StringBuilder("\uFEFF");
        out.append("Type;Nouveau compte ou tiers;Libellé;Ancien(s) compte(s);Solde ancien;Solde nouveau;Écart;Statut\r\n");
        append(out, "Compte", report.accounts());
        append(out, "Tiers", report.thirdParties());
        return out.toString();
    }

    private static void append(StringBuilder out, String type, List<Line> lines) {
        for (Line line : lines) {
            out.append(type).append(';')
                    .append(cell(line.key())).append(';')
                    .append(cell(line.label())).append(';')
                    .append(cell(String.join(" + ", line.oldAccounts()))).append(';')
                    .append(amount(line.oldBalance())).append(';')
                    .append(amount(line.newBalance())).append(';')
                    .append(amount(line.difference())).append(';')
                    .append(STATUS_LABELS.get(line.status())).append("\r\n");
        }
    }

    private static String amount(BigDecimal value) {
        return value.toPlainString().replace('.', ',');
    }

    private static String cell(String value) {
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
