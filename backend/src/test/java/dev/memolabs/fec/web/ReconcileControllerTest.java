package dev.memolabs.fec.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReconcileController.class)
class ReconcileControllerTest {

    private static final Path SAMPLES = Path.of("..", "samples");

    @Autowired
    private MockMvc mvc;

    private static MockMultipartFile sample(String part, String file) throws IOException {
        return new MockMultipartFile(part, file, "text/plain", Files.readAllBytes(SAMPLES.resolve(file)));
    }

    @Test
    void returnsTheReportAsJson() throws Exception {
        mvc.perform(multipart("/api/reconcile")
                        .file(sample("oldFec", "000000000FEC20260630-ancien.txt"))
                        .file(sample("newFec", "000000000FEC20261231-nouveau.txt"))
                        .file(sample("mapping", "correspondance-comptes.csv")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("DIFFERENCES"))
                .andExpect(jsonPath("$.mode").value("OPENING"))
                .andExpect(jsonPath("$.accounts[0].key").value("411100"))
                .andExpect(jsonPath("$.oldFile.encoding").value("ISO-8859-15"));
    }

    @Test
    void returnsTheCsvTable() throws Exception {
        mvc.perform(multipart("/api/reconcile/csv")
                        .file(sample("oldFec", "000000000FEC20260630-ancien.txt"))
                        .file(sample("newFec", "000000000FEC20261231-nouveau.txt"))
                        .file(sample("mapping", "correspondance-comptes.csv")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    void asksForTheMissingFile() throws Exception {
        mvc.perform(multipart("/api/reconcile").file(sample("oldFec", "000000000FEC20260630-ancien.txt")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Fichier manquant"));
    }

    @Test
    void explainsAnUnreadableFile() throws Exception {
        MockMultipartFile notAFec = new MockMultipartFile("oldFec", "bilan.csv", "text/csv",
                "Compte;Solde\n".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/reconcile")
                        .file(notAFec)
                        .file(sample("newFec", "000000000FEC20261231-nouveau.txt")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Fichier illisible"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("bilan.csv")));
    }
}
