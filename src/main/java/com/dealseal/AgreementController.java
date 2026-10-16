package com.dealseal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.regex.*;

@RestController
@RequestMapping("/api")
public class AgreementController {
    public record AnalyzeRequest(@NotBlank String conversation) {}
    public record Term(String field, String value, String status, String evidence, String question) {}
    public record Analysis(String status, boolean readyForSignature, List<Term> terms, List<String> warnings) {}

    @PostMapping("/analyze")
    public Analysis analyze(@Valid @RequestBody AnalyzeRequest request) {
        String text = request.conversation();
        List<Term> terms = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        // Demo heuristic only: deliberately conservative, not an AI determination of mutual assent.
        Matcher money = Pattern.compile("\\$\\s?([0-9][0-9,]*(?:\\.[0-9]{2})?)").matcher(text);
        LinkedHashSet<String> prices = new LinkedHashSet<>();
        while (money.find()) prices.add("$" + money.group(1));
        if (prices.size() > 1) {
            terms.add(new Term("Price", String.join(" → ", prices), "REVIEW", "Multiple monetary amounts appear", "Confirm the total price and any deposit or milestones."));
        } else if (prices.size() == 1) {
            terms.add(new Term("Price", prices.iterator().next(), "UNCONFIRMED", "Amount mentioned in conversation", "Have both parties explicitly confirm the amount."));
        } else {
            terms.add(new Term("Price", "Not found", "MISSING", "No currency amount detected", "What is the agreed price and currency?"));
        }
        Matcher dates = Pattern.compile("(?i)\\b(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:,?\\s+\\d{4})?\\b").matcher(text);
        LinkedHashSet<String> foundDates = new LinkedHashSet<>();
        while (dates.find()) foundDates.add(dates.group());
        if (foundDates.size() > 1) terms.add(new Term("Dates", String.join(" → ", foundDates), "REVIEW", "Different dates detected", "Which date applies to delivery, and which to payment?"));
        else if (foundDates.size() == 1) terms.add(new Term("Date", foundDates.iterator().next(), "UNCONFIRMED", "Date mentioned", "Have both parties confirmed this deadline?"));
        else terms.add(new Term("Delivery date", "Not found", "MISSING", "No date detected", "What is the delivery deadline?"));
        Matcher revisions = Pattern.compile("(?i)\\b(\\d+)\\s+(?:rounds? of )?revisions?\\b").matcher(text);
        if (revisions.find()) terms.add(new Term("Revisions", revisions.group(), "UNCONFIRMED", "Revision count mentioned", "Confirm what is included and when revisions must be requested."));
        warnings.add("Demo extraction only. This engine cannot establish agreement or legal enforceability.");
        warnings.add("Verify identity, scope, acceptance criteria, governing law and signature consent before signing.");
        return new Analysis("NEEDS_REVIEW", false, terms, warnings);
    }

    @GetMapping("/health")
    public Map<String,String> health() { return Map.of("status", "ok", "version", "0.1.0"); }
}
