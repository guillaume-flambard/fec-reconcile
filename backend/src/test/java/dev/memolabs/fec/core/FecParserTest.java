package dev.memolabs.fec.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FecParserTest {

    private static final String HEADER = "JournalCode|JournalLib|EcritureNum|EcritureDate|CompteNum|CompteLib|"
            + "CompAuxNum|CompAuxLib|PieceRef|PieceDate|EcritureLib|Debit|Credit|EcritureLet|DateLet|ValidDate|"
            + "Montantdevise|Idevise";

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void readsPipeSeparatedUtf8WithDecimalComma() {
        String fec = HEADER + "\n"
                + "VE|Ventes|1|20260215|411000|Clients|C001|Mairie|FA1|20260215|Vente|1200,50|0,00||||| \n"
                + "VE|Ventes|1|20260215|706000|Prestations|||FA1|20260215|Vente|0,00|1200,50|||||\n";

        FecFile file = FecParser.parse("test.txt", utf8(fec));

        assertThat(file.separator()).isEqualTo('|');
        assertThat(file.encoding()).isEqualTo("UTF-8");
        assertThat(file.entries()).hasSize(2);
        FecEntry first = file.entries().get(0);
        assertThat(first.ecritureDate()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(first.debit()).isEqualByComparingTo("1200.50");
        assertThat(first.compAuxNum()).isEqualTo("C001");
        assertThat(file.warnings()).isEmpty();
    }

    @Test
    void readsTabSeparatedIso885915() {
        String fec = HEADER.replace('|', '\t') + "\r\n"
                + "AC\tAchats\t1\t20260310\t607000\tAchats chêne\t\t\tB1\t20260310\tChêne\t800,00\t0,00\t\t\t\t\t\r\n"
                + "AC\tAchats\t1\t20260310\t401000\tFournisseurs\tF1\tJura\tB1\t20260310\tChêne\t0,00\t800,00\t\t\t\t\t\r\n";

        FecFile file = FecParser.parse("test.txt", fec.getBytes(Charset.forName("ISO-8859-15")));

        assertThat(file.separator()).isEqualTo('\t');
        assertThat(file.encoding()).isEqualTo("ISO-8859-15");
        assertThat(file.entries().get(0).compteLib()).isEqualTo("Achats chêne");
    }

    @Test
    void readsMontantAndSensInsteadOfDebitAndCredit() {
        String fec = "JournalCode|EcritureNum|EcritureDate|CompteNum|Montant|Sens\n"
                + "OD|1|20260101|512000|100,00|D\n"
                + "OD|1|20260101|101000|100,00|C\n";

        FecFile file = FecParser.parse("test.txt", utf8(fec));

        assertThat(file.entries().get(0).signedAmount()).isEqualByComparingTo("100");
        assertThat(file.entries().get(1).signedAmount()).isEqualByComparingTo("-100");
    }

    @Test
    void acceptsTheSignAtTheStartOrTheEnd() {
        assertThat(FecParser.amount("-12,30", "x")).isEqualByComparingTo("-12.30");
        assertThat(FecParser.amount("12,30-", "x")).isEqualByComparingTo("-12.30");
        assertThat(FecParser.amount("", "x")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void skipsAByteOrderMark() {
        String fec = "\uFEFF" + HEADER + "\nOD|x|1|20260101|512000||||||x|1,00|0,00|||||\n";

        FecFile file = FecParser.parse("test.txt", utf8(fec));

        assertThat(file.entries()).hasSize(1);
    }

    @Test
    void warnsAboutAnUnbalancedEntry() {
        String fec = HEADER + "\nOD|x|7|20260101|512000||||||x|10,00|0,00|||||\nOD|x|7|20260101|101000||||||x|0,00|9,00|||||\n";

        FecFile file = FecParser.parse("test.txt", utf8(fec));

        assertThat(file.warnings()).singleElement().asString().contains("OD 7").contains("1.00");
    }

    @Test
    void refusesAFileWithoutTheMandatoryColumns() {
        assertThatThrownBy(() -> FecParser.parse("bilan.csv", utf8("Compte|Solde\n512|10\n")))
                .isInstanceOf(FecFormatException.class)
                .hasMessageContaining("journalcode")
                .hasMessageContaining("Debit et Credit");
    }

    @Test
    void namesTheLineOfABadDate() {
        String fec = HEADER + "\nOD|x|1|2026-01-01|512000||||||x|1,00|0,00|||||\n";

        assertThatThrownBy(() -> FecParser.parse("test.txt", utf8(fec)))
                .isInstanceOf(FecFormatException.class)
                .hasMessageContaining("ligne 2")
                .hasMessageContaining("AAAAMMJJ");
    }

    @Test
    void refusesAFileWithoutSeparator() {
        assertThatThrownBy(() -> FecParser.parse("x.txt", utf8("JournalCode;EcritureNum\n")))
                .isInstanceOf(FecFormatException.class)
                .hasMessageContaining("tabulation");
    }
}
