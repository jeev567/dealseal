package com.dealseal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebsiteAnalysisNormalizerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WebsiteAnalysisNormalizer normalizer = new WebsiteAnalysisNormalizer();

    @Test void insertsMissingFieldsAndRemovesRedundantConfirmedQuestions() throws Exception {
        var input = mapper.readTree("""
            {"agreementStatus":"REVIEW_REQUIRED","readyForSignature":false,
             "terms":[{"field":"Price","value":"$2300","status":"CONFIRMED_IN_CHAT",
             "evidenceMessageIds":["m1"],"question":"Is this price final?"}],
             "warnings":[]}
            """);
        var result = normalizer.normalize(input);
        assertEquals(7, result.path("terms").size());
        assertEquals("", result.path("terms").get(0).path("question").asText());
        assertEquals("MISSING", result.path("terms").get(2).path("status").asText());
        assertEquals(0, result.path("terms").get(2).path("evidenceMessageIds").size());
        assertEquals("NEEDS_CLARIFICATION", result.path("agreementStatus").asText());
    }

    @Test void conflictWinsOverConfirmation() throws Exception {
        var input = mapper.readTree("""
            {"agreementStatus":"REVIEW_REQUIRED","readyForSignature":false,
             "terms":[
               {"field":"Price","value":"$2300","status":"CONFIRMED_IN_CHAT","evidenceMessageIds":["m1"],"question":""},
               {"field":"Price","value":"$2400","status":"CONFLICTED","evidenceMessageIds":["m2"],"question":"Which price?"}],
             "warnings":[]}
            """);
        assertEquals("CONFLICTED", normalizer.normalize(input).path("terms").get(0).path("status").asText());
    }
}
