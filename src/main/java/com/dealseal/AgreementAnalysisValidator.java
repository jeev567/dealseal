package com.dealseal;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class AgreementAnalysisValidator {
    private static final Set<String> STATUSES = Set.of("NEEDS_CLARIFICATION", "REVIEW_REQUIRED");
    private static final Set<String> TERM_STATUSES = Set.of("PROPOSED", "CONFIRMED_IN_CHAT", "CONFLICTED", "MISSING");

    public void validate(JsonNode result, Set<String> validMessageIds) {
        if (result == null || !result.isObject()
                || !STATUSES.contains(result.path("agreementStatus").asText())
                || !result.path("readyForSignature").isBoolean()
                || result.path("readyForSignature").asBoolean()
                || !result.path("terms").isArray()
                || result.path("terms").size() > 12
                || !result.path("warnings").isArray()) {
            throw new IllegalArgumentException("Invalid agreement analysis structure");
        }
        for (JsonNode warning : result.path("warnings")) {
            if (!warning.isTextual()) throw new IllegalArgumentException("Invalid warning");
        }
        for (JsonNode term : result.path("terms")) {
            if (!term.isObject()
                    || !nonBlank(term.path("field"))
                    || !term.path("value").isTextual()
                    || !TERM_STATUSES.contains(term.path("status").asText())
                    || !term.path("evidenceMessageIds").isArray()
                    || !term.path("question").isTextual()) {
                throw new IllegalArgumentException("Invalid term");
            }
            Set<String> seen = new HashSet<>();
            for (JsonNode id : term.path("evidenceMessageIds")) {
                if (!id.isTextual() || !validMessageIds.contains(id.asText()) || !seen.add(id.asText())) {
                    throw new IllegalArgumentException("Invalid or duplicate evidence reference");
                }
            }
            if ("MISSING".equals(term.path("status").asText()) && term.path("evidenceMessageIds").size() != 0) {
                throw new IllegalArgumentException("Missing terms cannot cite evidence");
            }
        }
    }

    private boolean nonBlank(JsonNode value) {
        return value.isTextual() && !value.asText().isBlank();
    }
}
