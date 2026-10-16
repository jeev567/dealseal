package com.dealseal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/contracts")
public class ContractPdfController {
    private final Path directory;
    private final ObjectMapper mapper;
    private static final Pattern ID = Pattern.compile("[a-f0-9-]{36}");

    public ContractPdfController(@Value("${dealseal.contracts.directory:./data/contracts}") String path, ObjectMapper mapper) {
        this.directory = Path.of(path).toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    public record Term(@NotBlank String field, @NotBlank String value) {}
    public record IssueRequest(@NotBlank String buyer, @NotBlank String seller,
                               @NotEmpty List<@Valid Term> terms) {}
    public record Receipt(String contractId, int version, String sha256, String issuedAt,
                          String downloadUrl, String status) {}
    public record Verification(String contractId, boolean matchesOriginal, String expectedSha256,
                               String suppliedSha256, String status) {}

    // DEMO ONLY: issuance is not authenticated, and does not constitute party approval or e-signature.
    @PostMapping("/issue")
    public Receipt issue(@Valid @RequestBody IssueRequest request) throws Exception {
        if (request.terms().size() > 30 || request.buyer().length() > 120 || request.seller().length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many or oversized fields");
        for (Term term : request.terms()) {
            if (term.field().length() > 120 || term.value().length() > 1000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Term too long");
        }
        Files.createDirectories(directory);
        String id = UUID.randomUUID().toString();
        String issuedAt = Instant.now().toString();
        byte[] pdf = render(id, issuedAt, request);
        String sha = sha256(pdf);
        // CREATE_NEW prevents accidental overwrite. Keep this directory private and backed up.
        Files.write(directory.resolve(id + ".pdf"), pdf, StandardOpenOption.CREATE_NEW);
        return new Receipt(id, 1, sha, issuedAt, "/api/contracts/" + id + "/download", "UNSIGNED_DRAFT");
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) throws Exception {
        byte[] pdf = readOriginal(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dealseal-" + id + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff").body(pdf);
    }

    @PostMapping(value="/{id}/verify", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Verification verify(@PathVariable String id, @RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > 5_000_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF must be 1 byte to 5 MB");
        String expected = sha256(readOriginal(id));
        String supplied = sha256(file.getBytes());
        boolean matches = MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                                                  supplied.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return new Verification(id, matches, expected, supplied, matches ? "MATCH" : "MODIFIED_OR_DIFFERENT");
    }

    private byte[] readOriginal(String id) throws Exception {
        if (!ID.matcher(id).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid contract ID");
        Path file = directory.resolve(id + ".pdf");
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found");
        return Files.readAllBytes(file);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private byte[] render(String id, String issuedAt, IssueRequest req) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            List<String> lines = new ArrayList<>();
            lines.add("Buyer: " + req.buyer());
            lines.add("Seller: " + req.seller());
            lines.add("Issued (UTC): " + issuedAt);
            lines.add("Contract ID: " + id + "  |  Version: 1");
            lines.add("");
            lines.add("AGREEMENT TERMS (DRAFT)");
            for (Term t : req.terms()) {
                lines.add(t.field() + ":");
                lines.add(t.value());
                lines.add("");
            }
            lines.add("NOT SIGNED. NOT PROOF OF MUTUAL APPROVAL.");
            lines.add("No payment is authorized by this document.");
            lines.add("Both parties must approve and sign a finalized agreement.");
            PDPage page = null;
            PDPageContentStream stream = null;
            float y = 0;
            for (String raw : lines) {
                for (String line : wrap(sanitize(raw), 88)) {
                    if (stream == null || y < 75) {
                        if (stream != null) stream.close();
                        page = new PDPage(PDRectangle.LETTER);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        y = 750;
                        stream.beginText();
                        stream.setFont(bold, 14);
                        stream.newLineAtOffset(45, y);
                        stream.showText("DEALSEAL - UNSIGNED DRAFT");
                        stream.endText();
                        y -= 30;
                    }
                    stream.beginText();
                    stream.setFont(font, 10);
                    stream.newLineAtOffset(45, y);
                    stream.showText(line);
                    stream.endText();
                    y -= 15;
                }
            }
            if (stream != null) stream.close();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static String sanitize(String s) {
        // Standard PDF Helvetica supports WinAnsi, not arbitrary Unicode.
        return s.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) return List.of("");
        while (text.length() > width) {
            int cut = text.lastIndexOf(' ', width);
            if (cut < 1) cut = width;
            lines.add(text.substring(0, cut));
            text = text.substring(cut).stripLeading();
        }
        lines.add(text);
        return lines;
    }
}
