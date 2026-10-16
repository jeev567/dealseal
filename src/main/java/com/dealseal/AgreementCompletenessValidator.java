package com.dealseal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Conservative website-service agreement checklist. This is a demo safeguard,
 * not a substitute for legal review or proof of agreement.
 */
@Component
public class AgreementCompletenessValidator {
    private static final Map<String, Pattern> REQUIRED = new LinkedHashMap<>();
    static {
        REQUIRED.put("price", Pattern.compile("(?i)\\b(price|total cost|total fee|project fee|budget)\\b"));
        REQUIRED.put("deliveryDate", Pattern.compile("(?i)\\b(delivery|deadline|completion date|due date)\\b"));
        REQUIRED.put("scopeOfWork", Pattern.compile("(?i)\\b(scope|deliverables|features|pages|work included)\\b"));
        REQUIRED.put("revisions", Pattern.compile("(?i)\\b(revisions?|revision count|rounds of changes)\\b"));
        REQUIRED.put("paymentSchedule", Pattern.compile("(?i)\\b(payment|deposit|installments?|milestones?)\\b"));
        REQUIRED.put("acceptanceCriteria", Pattern.compile("(?i)\\b(acceptance|approval criteria|sign.off|testing criteria)\\b"));
        REQUIRED.put("intellectualProperty", Pattern.compile("(?i)\\b(intellectual property|ip ownership|source code ownership|ownership|copyright)\\b"));
    }

    public record Checklist(Map<String, Boolean> confirmed, List<String> missing, boolean complete) {}

    public Checklist check(List<AgreementDraftController.Term> terms) {
        if (terms == null || terms.isEmpty() || terms.size() > 12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide 1 to 12 analyzed terms");
        Map<String, Boolean> confirmed = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (var requirement : REQUIRED.entrySet()) {
            boolean matched = false;
            for (var term : terms) {
                if (term == null || term.field() == null || term.status() == null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid term");
                // Match field labels, not values: a vague "website development" value cannot prove scope.
                if (requirement.getValue().matcher(term.field()).find()
                        && "CONFIRMED_IN_CHAT".equals(term.status())
                        && term.value() != null && !term.value().isBlank()
                        && term.evidenceMessageIds() != null && !term.evidenceMessageIds().isEmpty()) {
                    matched = true;
                    break;
                }
            }
            confirmed.put(requirement.getKey(), matched);
            if (!matched) missing.add(requirement.getKey());
        }
        return new Checklist(Collections.unmodifiableMap(confirmed), List.copyOf(missing), missing.isEmpty());
    }

    public void requireComplete(List<AgreementDraftController.Term> terms) {
        Checklist checklist = check(terms);
        if (!checklist.complete())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Agreement incomplete; missing or unconfirmed: " + String.join(", ", checklist.missing()));
    }
}
