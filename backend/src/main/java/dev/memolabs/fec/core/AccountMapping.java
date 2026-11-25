package dev.memolabs.fec.core;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Correspondence between the old chart of accounts and the new one: one "old;new" pair per line
 * (separator ; , or tab). Several old accounts may point to the same new account. An account that
 * is not listed keeps its number.
 */
public final class AccountMapping {

    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\n|\r");
    private static final AccountMapping IDENTITY = new AccountMapping(Map.of());

    private final Map<String, String> oldToNew;

    private AccountMapping(Map<String, String> oldToNew) {
        this.oldToNew = oldToNew;
    }

    public static AccountMapping identity() {
        return IDENTITY;
    }

    public static AccountMapping parse(byte[] content) {
        if (content == null || content.length == 0) {
            return IDENTITY;
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        Map<String, String> pairs = new LinkedHashMap<>();
        String[] lines = LINE_BREAK.split(text);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || (i == 0 && isHeader(line))) {
                continue;
            }
            String[] cells = line.split("[;,\t]", -1);
            if (cells.length < 2 || cells[0].isBlank() || cells[1].isBlank()) {
                throw new FecFormatException("Table de correspondance, ligne " + (i + 1)
                        + " : il faut « ancien compte ; nouveau compte ».");
            }
            String oldAccount = cells[0].trim();
            String newAccount = cells[1].trim();
            String previous = pairs.putIfAbsent(oldAccount, newAccount);
            if (previous != null && !previous.equals(newAccount)) {
                throw new FecFormatException("Table de correspondance, ligne " + (i + 1) + " : le compte "
                        + oldAccount + " pointe à la fois vers " + previous + " et vers " + newAccount + ".");
            }
        }
        return new AccountMapping(Collections.unmodifiableMap(pairs));
    }

    private static boolean isHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("ancien") || lower.contains("old") || lower.contains("source");
    }

    /** New account for an old one, or the same number when the table does not list it. */
    public String map(String oldAccount) {
        return oldToNew.getOrDefault(oldAccount, oldAccount);
    }

    public boolean isMapped(String oldAccount) {
        return oldToNew.containsKey(oldAccount);
    }

    public boolean isEmpty() {
        return oldToNew.isEmpty();
    }
}
