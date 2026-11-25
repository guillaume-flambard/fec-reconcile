package dev.memolabs.fec.core;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads a flat FEC file as described by article A47 A-1 of the LPF: a header line with the field
 * names, tab or pipe separated, UTF-8 or ISO 8859-15, dates as AAAAMMJJ, decimal comma, sign at
 * the start or the end of the amount. Debit and Credit may be replaced by Montant and Sens.
 */
public final class FecParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\n|\r");
    private static final int MAX_REPORTED_ERRORS = 10;
    private static final int MAX_REPORTED_WARNINGS = 20;

    private FecParser() {
    }

    public static FecFile parse(String name, byte[] content) {
        if (content.length == 0) {
            throw new FecFormatException("Le fichier « " + name + " » est vide.");
        }
        Charset charset = detectCharset(content);
        String text = new String(content, charset);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String[] lines = LINE_BREAK.split(text);
        String header = lines[0];
        char separator = detectSeparator(name, header);
        Map<String, Integer> columns = readHeader(name, header, separator);

        List<FecEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int errorCount = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            try {
                entries.add(readLine(i + 1, line, separator, columns));
            } catch (FecFormatException e) {
                errorCount++;
                if (errors.size() < MAX_REPORTED_ERRORS) {
                    errors.add(e.getMessage());
                }
            }
        }
        if (errorCount > 0) {
            String more = errorCount > errors.size() ? " (et " + (errorCount - errors.size()) + " autres)" : "";
            throw new FecFormatException("Le fichier « " + name + " » contient " + errorCount
                    + " ligne(s) illisible(s)" + more + " : " + String.join(" ; ", errors));
        }
        if (entries.isEmpty()) {
            throw new FecFormatException("Le fichier « " + name + " » ne contient aucune écriture.");
        }
        return new FecFile(name, charset.name(), separator, List.copyOf(entries), unbalancedEntries(entries));
    }

    /** UTF-8 when the bytes are valid UTF-8, ISO 8859-15 otherwise (the two encodings the text allows besides ASCII). */
    private static Charset detectCharset(byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return Charset.forName("ISO-8859-15");
        }
    }

    private static char detectSeparator(String name, String header) {
        if (header.indexOf('\t') >= 0) {
            return '\t';
        }
        if (header.indexOf('|') >= 0) {
            return '|';
        }
        throw new FecFormatException("Le fichier « " + name
                + " » n'a ni tabulation ni « | » dans sa première ligne : ce n'est pas un FEC à plat.");
    }

    private static Map<String, Integer> readHeader(String name, String header, char separator) {
        String[] fields = split(header, separator);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < fields.length; i++) {
            columns.put(fields[i].trim().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = new ArrayList<>();
        for (String required : List.of("journalcode", "ecriturenum", "ecrituredate", "comptenum")) {
            if (!columns.containsKey(required)) {
                missing.add(required);
            }
        }
        boolean debitCredit = columns.containsKey("debit") && columns.containsKey("credit");
        boolean amountSense = columns.containsKey("montant") && columns.containsKey("sens");
        if (!debitCredit && !amountSense) {
            missing.add("Debit et Credit (ou Montant et Sens)");
        }
        if (!missing.isEmpty()) {
            throw new FecFormatException("Le fichier « " + name + " » n'a pas les colonnes obligatoires : "
                    + String.join(", ", missing) + ".");
        }
        return columns;
    }

    private static FecEntry readLine(int lineNumber, String line, char separator, Map<String, Integer> columns) {
        String[] fields = split(line, separator);
        String where = "ligne " + lineNumber;
        BigDecimal debit;
        BigDecimal credit;
        if (columns.containsKey("debit") && columns.containsKey("credit")) {
            debit = amount(field(fields, columns, "debit"), where);
            credit = amount(field(fields, columns, "credit"), where);
        } else {
            BigDecimal value = amount(field(fields, columns, "montant"), where);
            String sense = field(fields, columns, "sens").trim().toUpperCase(Locale.ROOT);
            switch (sense) {
                case "D", "+1", "1" -> {
                    debit = value;
                    credit = BigDecimal.ZERO;
                }
                case "C", "-1" -> {
                    debit = BigDecimal.ZERO;
                    credit = value;
                }
                default -> throw new FecFormatException(where + " : sens « " + sense + " » inconnu (attendu D ou C)");
            }
        }
        String compteNum = field(fields, columns, "comptenum").trim();
        if (compteNum.isEmpty()) {
            throw new FecFormatException(where + " : numéro de compte vide");
        }
        return new FecEntry(
                lineNumber,
                field(fields, columns, "journalcode").trim(),
                field(fields, columns, "ecriturenum").trim(),
                date(field(fields, columns, "ecrituredate"), where),
                compteNum,
                field(fields, columns, "comptelib").trim(),
                field(fields, columns, "compauxnum").trim(),
                field(fields, columns, "compauxlib").trim(),
                field(fields, columns, "pieceref").trim(),
                field(fields, columns, "ecriturelib").trim(),
                debit,
                credit,
                field(fields, columns, "ecriturelet").trim());
    }

    /** Field value, or an empty string when the column is absent or the line is short. */
    private static String field(String[] fields, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= fields.length) {
            return "";
        }
        return fields[index];
    }

    static BigDecimal amount(String raw, String where) {
        String value = raw.trim().replace(" ", "").replace("\u00A0", "");
        if (value.isEmpty()) {
            return BigDecimal.ZERO;
        }
        boolean negative = false;
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (first == '-' || first == '+') {
            negative = first == '-';
            value = value.substring(1);
        } else if (last == '-' || last == '+') {
            negative = last == '-';
            value = value.substring(0, value.length() - 1);
        }
        try {
            BigDecimal parsed = new BigDecimal(value.replace(',', '.'));
            return negative ? parsed.negate() : parsed;
        } catch (NumberFormatException e) {
            throw new FecFormatException(where + " : montant « " + raw.trim() + " » illisible");
        }
    }

    private static LocalDate date(String raw, String where) {
        String value = raw.trim();
        try {
            return LocalDate.parse(value, DATE);
        } catch (DateTimeParseException e) {
            throw new FecFormatException(where + " : date « " + value + " » illisible (attendu AAAAMMJJ)");
        }
    }

    private static String[] split(String line, char separator) {
        return line.split(Pattern.quote(String.valueOf(separator)), -1);
    }

    /** One warning per entry (journal and number) whose debits and credits differ. */
    private static List<String> unbalancedEntries(List<FecEntry> entries) {
        Map<String, BigDecimal> balances = new LinkedHashMap<>();
        for (FecEntry entry : entries) {
            balances.merge(entry.journalCode() + " " + entry.ecritureNum(), entry.signedAmount(), BigDecimal::add);
        }
        List<String> warnings = new ArrayList<>();
        long unbalanced = balances.values().stream().filter(b -> b.signum() != 0).count();
        balances.forEach((key, balance) -> {
            if (balance.signum() != 0 && warnings.size() < MAX_REPORTED_WARNINGS) {
                warnings.add("Écriture " + key + " déséquilibrée de " + balance.toPlainString() + " €");
            }
        });
        if (unbalanced > warnings.size()) {
            warnings.add("… et " + (unbalanced - warnings.size()) + " autres écritures déséquilibrées");
        }
        return List.copyOf(warnings);
    }
}
