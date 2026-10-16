package com.dealseal.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Guided negotiation: suggest questions, collect party statements, then reanalyze.
 * Answers never automatically become confirmed terms or legal assent.
 */
@RestController
@RequestMapping("/api/agreements/negotiation")
public class AgreementNegotiationController {
    public record StartRequest(@NotNull List<AgreementDraftController.Term> terms) {}
    public record Question(String field, String status, String question) {}
    public record Answer(@NotBlank String party, @NotBlank String field, @NotBlank String response) {}
    public record ContinueRequest(@NotBlank String conversation, @NotNull List<Answer> answers) {}
    public record ContinueResponse(String updatedConversation, String nextStep, boolean readyForSignature) {}

    @PostMapping("/questions")
    public List<Question> questions(@Valid @RequestBody StartRequest request) {
        if (request.terms() == null || request.terms().isEmpty() || request.terms().size() > 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide 1 to 12 analyzed terms");
        List<Question> result = new ArrayList<>();
        for (var term : request.terms()) {
            if (term == null || term.field() == null || term.field().isBlank() ||
                    !Set.of("CONFIRMED_IN_CHAT", "PROPOSED", "MISSING", "CONFLICTED").contains(term.status()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid term");
            if (!"CONFIRMED_IN_CHAT".equals(term.status())) {
                String q = term.question();
                if (q == null || q.isBlank()) q = "What should both parties agree for " + term.field() + "?";
                result.add(new Question(term.field(), term.status(), q));
            }
        }
        return List.copyOf(result);
    }

    @PostMapping("/continue")
    public ContinueResponse continueConversation(@Valid @RequestBody ContinueRequest request) {
        if (request.conversation().length() > 15000 || request.answers().size() > 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation or answer limit exceeded");
        StringBuilder updated = new StringBuilder(request.conversation());
        for (Answer answer : request.answers()) {
            if (answer == null || answer.party() == null || answer.field() == null || answer.response() == null ||
                    !Set.of("CLIENT", "DEVELOPER").contains(answer.party().toUpperCase(java.util.Locale.ROOT)) ||
                    answer.field().isBlank() || answer.response().isBlank() ||
                    answer.field().length() > 100 || answer.response().length() > 1000 ||
                    answer.field().contains("\n") || answer.response().contains("\n") ||
                    answer.field().contains("\r") || answer.response().contains("\r"))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid answer");
            updated.append("\n").append(answer.party().toUpperCase(java.util.Locale.ROOT))
                    .append(": Regarding ").append(answer.field()).append(", ").append(answer.response());
        }
        if (updated.length() > 15000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Updated conversation exceeds 15000 characters");
        return new ContinueResponse(updated.toString(),
                "Submit updatedConversation to POST /api/ai/analyze, then review unresolved terms again.", false);
    }
}
