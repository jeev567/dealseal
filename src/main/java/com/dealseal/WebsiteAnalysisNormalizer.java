package com.dealseal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalize website agreement analysis to seven canonical fields.
 * Missing categories are explicitly MISSING, never silently considered agreed.
 * Does not infer consent or upgrade any term to confirmed.
 */
@Component
public class WebsiteAnalysisNormalizer {
    private static final Map<String, Pattern> FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> QUESTIONS = Map.of(
            "Price", "What is the agreed total price?",
            "Delivery Date", "What is the agreed delivery deadline, including year?",
            "Scope of Work", "Which pages, features, and deliverables are included?",
            "Revisions", "How many revisions are included?",
            "Payment Schedule", "When and how much will each party pay?",
            "Acceptance Criteria", "How will the client verify and accept the deliverables?",
            "IP Ownership", "Who owns the deliverables and when does ownership transfer?");
    static {
        FIELDS.put("Price", Pattern.compile("(?i)\\b(price|total cost|total fee|project fee|budget)\\b"));
        FIELDS.put("Delivery Date", Pattern.compile("(?i)\\b(delivery|deadline|completion date|due date)\\b"));
        FIELDS.put("Scope of Work", Pattern.compile("(?i)\\b(scope|deliverables|features|pages|work included)\\b"));
        FIELDS.put("Revisions", Pattern.compile("(?i)\\b(revisions?|revision count|rounds of changes)\\b"));
        FIELDS.put("Payment Schedule", Pattern.compile("(?i)\\b(payment|deposit|installments?|milestones?)\\b"));
        FIELDS.put("Acceptance Criteria", Pattern.compile("(?i)\\b(acceptance|approval criteria|sign.off|testing criteria)\\b"));
        FIELDS.put("IP Ownership", Pattern.compile("(?i)\\b(intellectual property|ip ownership|source code ownership|ownership|copyright)\\b"));
    }

    public ObjectNode normalize(JsonNode input) {
        ObjectNode result = input.deepCopy();
        ArrayNode original = (ArrayNode) input.path("terms");
        ArrayNode terms = result.arrayNode();
        for (var field : FIELDS.entrySet()) {
            JsonNode selected = null;
            for (JsonNode candidate : original) {
                if (field.getValue().matcher(candidate.path("field").asText()).find()) {
                    // Never prefer an optimistic confirmation over a conflicting or unresolved candidate.
                    if (selected == null || rank(candidate.path("status").asText()) < rank(selected.path("status").asText()))
                        selected = candidate;
                }
            }
            ObjectNode term = terms.addObject();
            term.put("field", field.getKey());
            if (selected == null) {
                term.put("value", "Not specified");
                term.put("status", "MISSING");
                term.putArray("evidenceMessageIds");
                term.put("question", QUESTIONS.get(field.getKey()));
            } else {
                term.put("value", selected.path("value").asText());
                term.put("status", selected.path("status").asText());
                term.set("evidenceMessageIds", selected.path("evidenceMessageIds").deepCopy());
                String question = selected.path("question").asText();
                term.put("question", "CONFIRMED_IN_CHAT".equals(selected.path("status").asText())
                        ? "" : question.isBlank() ? QUESTIONS.get(field.getKey()) : question);
            }
        }
        result.set("terms", terms);
        boolean unresolved = false;
        for (JsonNode term : terms) {
            if (!"CONFIRMED_IN_CHAT".equals(term.path("status").asText())) unresolved = true;
        }
        if (unresolved) result.put("agreementStatus", "NEEDS_CLARIFICATION");
        return result;
    }

    private int rank(String status) {
        return switch (status) {
            case "CONFLICTED" -> 0;
            case "MISSING" -> 1;
            case "PROPOSED" -> 2;
            case "CONFIRMED_IN_CHAT" -> 3;
            default -> 4;
        };
    }
}
