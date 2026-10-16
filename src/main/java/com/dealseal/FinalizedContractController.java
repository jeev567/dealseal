package com.dealseal;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/finalized-contracts")
public class FinalizedContractController {
    public record FinalizeRequest(UUID agreementId, int version) {}
    public record Receipt(String contractId, int version, String agreementSha256, String pdfSha256,
                          String status, String downloadUrl, String issuedAt) {}
    public record Verification(boolean matchesOriginal, String expectedSha256, String suppliedSha256, String status) {}
    private final LockedConsentController consents;
    private final Path directory;
    private final Map<String, Receipt> issued = new java.util.concurrent.ConcurrentHashMap<>();
    public FinalizedContractController(LockedConsentController consents,
          @Value("${dealseal.contracts.directory:./data/contracts}") String path) {
        this.consents = consents;
        directory = Path.of(path).toAbsolutePath().normalize().resolve("finalized");
    }
    private static String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    private static String key(UUID id, int version) { return id + "-v" + version; }
    private Path file(String id) {
        if (!id.matches("[0-9a-f-]{36}-v[1-9][0-9]*"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid contract reference");
        return directory.resolve(id + ".pdf");
    }
    @PostMapping("/issue")
    public synchronized Receipt issue(@RequestBody FinalizeRequest req) throws Exception {
        if (req == null || req.agreementId() == null || req.version() < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agreement and version required");
        var snap = consents.finalized(req.agreementId(), req.version());
        String id = key(req.agreementId(), req.version());
        Receipt existing = issued.get(id);
        if (existing != null) return existing;
        Files.createDirectories(directory);
        Path path = file(id);
        // Never overwrite an already issued document, including after a backend restart.
        if (Files.exists(path)) {
            byte[] previous = Files.readAllBytes(path);
            Receipt restored = new Receipt(id, req.version(), snap.sha256(), hash(previous),
                    "CONSENT_RECORDED_DEMO", "/api/finalized-contracts/" + id + "/download", "Previously issued");
            issued.put(id, restored);
            return restored;
        }
        String now = Instant.now().toString();
        byte[] pdf = render(snap, now);
        try { Files.write(path, pdf, StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException concurrent) { pdf = Files.readAllBytes(path); }
        Receipt receipt = new Receipt(id, req.version(), snap.sha256(), hash(pdf),
                "CONSENT_RECORDED_DEMO", "/api/finalized-contracts/" + id + "/download", now);
        issued.put(id, receipt);
        return receipt;
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) throws Exception {
        Path p = file(id);
        if (!Files.isRegularFile(p)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dealseal-" + id + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff").body(Files.readAllBytes(p));
    }
    @PostMapping(value="/{id}/verify", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Verification verify(@PathVariable String id, @RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > 5_000_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF must be 1 byte to 5 MB");
        Path p = file(id);
        if (!Files.isRegularFile(p)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        String expected = hash(Files.readAllBytes(p));
        String actual = hash(file.getBytes());
        boolean match = MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return new Verification(match, expected, actual, match ? "MATCH" : "MISMATCH");
    }
    private static byte[] render(LockedConsentController.FinalizedSnapshot snap, String issuedAt) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("DEALSEAL - AGREEMENT & CONSENT RECORD (DEMO)");
        lines.add("Agreement ID: " + snap.agreementId());
        lines.add("Locked version: " + snap.version());
        lines.add("Issued UTC: " + issuedAt);
        lines.add("Locked terms SHA-256: " + snap.sha256());
        lines.add("");
        lines.add("AGREED TERMS");
        int i = 1;
        for (String term : snap.terms()) {
            lines.add((i++) + ". " + term);
            lines.add("");
        }
        lines.add("INDEPENDENT DEMO CONSENT RECORDS");
        lines.add("Client: consent recorded at " + snap.clientApprovedAt() + " UTC");
        lines.add("Developer: consent recorded at " + snap.developerApprovedAt() + " UTC");
        lines.add("");
        lines.add("IMPORTANT: Links are bearer tokens, identities are not verified.");
        lines.add("This PDF is NOT digitally signed or legally verified.");
        lines.add("The PDF SHA-256 is returned separately in the issuance receipt.");
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPageContentStream stream = null;
            float y = 0;
            try {
                for (String raw : lines) {
                    String safe = raw.replaceAll("[^\\x20-\\x7E]", "?");
                    while (true) {
                        int limit = Math.min(90, safe.length());
                        int cut = safe.length() <= 90 ? safe.length() : safe.lastIndexOf(' ', limit);
                        if (cut < 1) cut = limit;
                        String line = safe.substring(0, cut);
                        if (stream == null || y < 65) {
                            if (stream != null) stream.close();
                            PDPage page = new PDPage(PDRectangle.LETTER);
                            doc.addPage(page);
                            stream = new PDPageContentStream(doc, page);
                            y = 750;
                        }
                        stream.beginText();
                        stream.setFont(raw.startsWith("DEALSEAL") || raw.equals("AGREED TERMS") ||
                                raw.startsWith("INDEPENDENT DEMO") ? bold : font, 10);
                        stream.newLineAtOffset(42, y);
                        stream.showText(line);
                        stream.endText();
                        y -= 15;
                        if (safe.length() <= cut) break;
                        safe = safe.substring(cut).stripLeading();
                    }
                }
            } finally { if (stream != null) stream.close(); }
            doc.save(output);
            return output.toByteArray();
        }
    }
}
