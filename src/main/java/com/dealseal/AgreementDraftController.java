package com.dealseal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/agreements")
public class AgreementDraftController {
    public record Term(String field, String value, String status, List<String> evidenceMessageIds, String question) {}
    public record DraftRequest(@NotNull List<@Valid Term> terms) {}
    public record DraftSection(String heading, String text, List<String> evidenceMessageIds) {}
    public record DraftResponse(String status, boolean readyForSignature, List<DraftSection> sections, List<String> openQuestions, List<String> warnings) {}

    @PostMapping("/draft")
    public DraftResponse draft(@Valid @RequestBody DraftRequest request) {
        if (request.terms().isEmpty() || request.terms().size() > 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide 1 to 12 analyzed terms");
        List<DraftSection> sections = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        for (Term term : request.terms()) {
            if (term == null || term.field() == null || term.field().isBlank()
                    || term.status() == null || !Set.of("PROPOSED", "CONFIRMED_IN_CHAT", "CONFLICTED", "MISSING").contains(term.status()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid term");
            boolean unresolved = !"CONFIRMED_IN_CHAT".equals(term.status());
            String value = term.value() == null || term.value().isBlank() ? "Not specified" : term.value();
            String sectionText = "MISSING".equals(term.status())
                    ? "[TO BE AGREED] " + term.field()
                    : value + (unresolved ? " [REQUIRES EXPLICIT CONFIRMATION]" : " [REPORTED AS CONFIRMED IN CHAT; VERIFY]");
            sections.add(new DraftSection(term.field(), sectionText,
                    term.evidenceMessageIds() == null ? List.of() : List.copyOf(term.evidenceMessageIds())));
            if (unresolved) questions.add(term.question() == null || term.question().isBlank()
                    ? "Confirm " + term.field() + " with both parties." : term.question());
        }
        return new DraftResponse("DRAFT_REQUIRES_REVIEW", false, List.copyOf(sections), List.copyOf(questions),
                List.of("Draft for discussion only; not a signed or legally verified contract.",
                        "Both parties must verify all terms, identities, legal requirements and consent before signing."));
    }
}
