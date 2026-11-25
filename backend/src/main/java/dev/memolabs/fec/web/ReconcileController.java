package dev.memolabs.fec.web;

import dev.memolabs.fec.core.AccountMapping;
import dev.memolabs.fec.core.FecFile;
import dev.memolabs.fec.core.FecParser;
import dev.memolabs.fec.core.ReconciliationCsv;
import dev.memolabs.fec.core.ReconciliationReport;
import dev.memolabs.fec.core.ReconciliationReport.Mode;
import dev.memolabs.fec.core.Reconciler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Files are read in memory and never written to disk or logged: a FEC holds the whole accounting
 * of a company.
 */
@RestController
@RequestMapping("/api/reconcile")
public class ReconcileController {

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ReconciliationReport reconcile(
            @RequestParam("oldFec") MultipartFile oldFec,
            @RequestParam("newFec") MultipartFile newFec,
            @RequestParam(value = "mapping", required = false) MultipartFile mapping,
            @RequestParam(value = "mode", defaultValue = "AUTO") Mode mode,
            @RequestParam(value = "openingJournals", defaultValue = "AN,RAN,ANO") String openingJournals,
            @RequestParam(value = "balanceSheetOnly", defaultValue = "false") boolean balanceSheetOnly)
            throws IOException {
        FecFile oldFile = FecParser.parse(name(oldFec, "ancien FEC"), oldFec.getBytes());
        FecFile newFile = FecParser.parse(name(newFec, "nouveau FEC"), newFec.getBytes());
        AccountMapping accountMapping = mapping == null || mapping.isEmpty()
                ? AccountMapping.identity()
                : AccountMapping.parse(mapping.getBytes());
        List<String> journals = Arrays.asList(openingJournals.split(","));
        return Reconciler.compare(oldFile, newFile, accountMapping, mode, journals, balanceSheetOnly);
    }

    @PostMapping(value = "/csv", produces = "text/csv")
    public ResponseEntity<byte[]> reconcileCsv(
            @RequestParam("oldFec") MultipartFile oldFec,
            @RequestParam("newFec") MultipartFile newFec,
            @RequestParam(value = "mapping", required = false) MultipartFile mapping,
            @RequestParam(value = "mode", defaultValue = "AUTO") Mode mode,
            @RequestParam(value = "openingJournals", defaultValue = "AN,RAN,ANO") String openingJournals,
            @RequestParam(value = "balanceSheetOnly", defaultValue = "false") boolean balanceSheetOnly)
            throws IOException {
        ReconciliationReport report = reconcile(oldFec, newFec, mapping, mode, openingJournals, balanceSheetOnly);
        byte[] body = ReconciliationCsv.toCsv(report).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"table-de-correspondance-et-reconciliation.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static String name(MultipartFile file, String fallback) {
        String original = file.getOriginalFilename();
        return original == null || original.isBlank() ? fallback : original;
    }
}
