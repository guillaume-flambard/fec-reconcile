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
 *
 * <p>Real files often stray from the text: re-saved from Excel with semicolons, Windows encoding,
 * quoted values, dates as JJ/MM/AAAA. These are read anyway and reported as warnings, because the
 * comparison only needs accounts and amounts. A line is refused only when its account or amount
 * cannot be read.
 */
public final class FecParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuuMMdd"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("uuuu-MM-dd"));
    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\n|\r");
    private static final int MAX_REPORTED_ERRORS = 10;
    private static final int MAX_REPORTED_WARNINGS = 20;

    private FecParser() {
    }

    public static FecFile parse(String name, byte[] content) {
        if (content.length == 0) {
            throw new FecFormatException("Le fichier « " + name + " » est vide.");
        }
        List<String> warnings = new ArrayList<>();
        Charset charset = detectCharset(content);
        if (charset.name().equals("windows-1252")) {
            warnings.add("Encodage Windows (cp1252) : le texte demande UTF-8 ou ISO 8859-15. Lu quand même.");
        }
        String text = new String(content, charset);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String[] lines = LINE_BREAK.split(text);
        String header = lines[0];
        char separator = detectSeparator(name, header);
        if (separator == ';') {
            warnings.add("Séparateur « ; » (fichier réenregistré dans un tableur ?) : le texte demande une "
                    + "tabulation ou « | ». Lu quand même.");
        }
        Map<String, Integer> columns = readHeader(name, header, separator);
        String firstColumn = unquote(split(header, separator)[0]).toLowerCase(Locale.ROOT);

        List<FecEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int errorCount = 0;
        int repeatedHeaders = 0;
        int nonStandardDates = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] fields = split(line, separator);
            if (unquote(fields[0]).toLowerCase(Locale.ROOT).equals(firstColumn)) {
                repeatedHeaders++;
                continue;
            }
            try {
                FecEntry entry = readLine(i + 1, fields, columns);
                if (entry.ecritureDate() == null || !isStandardDate(field(fields, columns, "ecrituredate"))) {
                    nonStandardDates++;
                }
                entries.add(entry);
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
        if (repeatedHeaders > 0) {
            warnings.add(repeatedHeaders + " ligne(s) d'en-tête répétée(s) au milieu du fichier, ignorée(s).");
        }
        if (nonStandardDates > 0) {
            warnings.add(nonStandardDates + " ligne(s) avec une date d'écriture absente ou hors du format "
                    + "AAAAMMJJ. Sans effet sur la comparaison, mais non conforme pour un contrôle fiscal.");
        }
        warnings.addAll(unbalancedEntries(entries));
        return new FecFile(name, charset.name(), separator, List.copyOf(entries), List.copyOf(warnings));
    }

    /**
     * UTF-8 when the bytes are valid UTF-8. Otherwise Windows-1252 if a byte falls in 0x80-0x9F (the
     * range where it differs from ISO 8859-15, and where Sage and Excel put €, œ and ’), else ISO 8859-15.
     */
    private static Charset detectCharset(byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            for (byte b : content) {
                int unsigned = b & 0xFF;
                if (unsigned >= 0x80 && unsigned <= 0x9F) {
                    return Charset.forName("windows-1252");
                }
            }
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
        if (header.indexOf(';') >= 0) {
            return ';';
        }
        throw new FecFormatException("Le fichier « " + name
                + " » n'a ni tabulation ni « | » dans sa première ligne : ce n'est pas un FEC à plat.");
    }

    private static Map<String, Integer> readHeader(String name, String header, char separator) {
        String[] fields = split(header, separator);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < fields.length; i++) {
            columns.putIfAbsent(unquote(fields[i]).toLowerCase(Locale.ROOT), i);
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

    private static FecEntry readLine(int lineNumber, String[] fields, Map<String, Integer> columns) {
        String where = "ligne " + lineNumber;
        BigDecimal debit;
        BigDecimal credit;
        if (columns.containsKey("debit") && columns.containsKey("credit")) {
            debit = amount(field(fields, columns, "debit"), where);
            credit = amount(field(fields, columns, "credit"), where);
        } else {
            BigDecimal value = amount(field(fields, columns, "montant"), where);
            String sense = field(fields, columns, "sens").toUpperCase(Locale.ROOT);
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
        String compteNum = field(fields, columns, "comptenum");
        if (compteNum.isEmpty()) {
            throw new FecFormatException(where + " : numéro de compte vide");
        }
        return new FecEntry(
                lineNumber,
                field(fields, columns, "journalcode"),
                field(fields, columns, "ecriturenum"),
                date(field(fields, columns, "ecrituredate")),
                compteNum,
                field(fields, columns, "comptelib"),
                field(fields, columns, "compauxnum"),
                field(fields, columns, "compauxlib"),
                field(fields, columns, "pieceref"),
                field(fields, columns, "ecriturelib"),
                debit,
                credit,
                field(fields, columns, "ecriturelet"));
    }

    /** Trimmed, unquoted field value, or an empty string when the column is absent or the line is short. */
    private static String field(String[] fields, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        if (index == null || index >= fields.length) {
            return "";
        }
        return unquote(fields[index]);
    }

    /** Removes the surrounding double quotes a spreadsheet adds, and undoubles inner ones. */
    static String unquote(String raw) {
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).replace("\"\"", "\"").trim();
        }
        return value;
    }

    static BigDecimal amount(String raw, String where) {
        String value = raw.trim().replace(" ", "").replace("\u00A0", "").replace("\u202F", "");
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
        // "1.234,56": the dot groups thousands, the comma marks the decimals.
        if (value.indexOf(',') >= 0 && value.indexOf('.') >= 0) {
            value = value.replace(".", "");
        }
        try {
            BigDecimal parsed = new BigDecimal(value.replace(',', '.'));
            return negative ? parsed.negate() : parsed;
        } catch (NumberFormatException e) {
            throw new FecFormatException(where + " : montant « " + raw.trim() + " » illisible");
        }
    }

    /** The entry date, or null when it is missing or unreadable: the comparison does not use it. */
    private static LocalDate date(String raw) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, format);
            } catch (DateTimeParseException e) {
                // try the next format
            }
        }
        return null;
    }

    private static boolean isStandardDate(String raw) {
        try {
            LocalDate.parse(raw, DATE_FORMATS.get(0));
            return true;
        } catch (DateTimeParseException e) {
            return false;
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
                warnings.add("Écriture " + key.trim() + " déséquilibrée de " + Amounts.french(balance) + " €.");
            }
        });
        if (unbalanced > warnings.size()) {
            warnings.add("Et " + (unbalanced - warnings.size()) + " autres écritures déséquilibrées.");
        }
        return warnings;
    }
}
