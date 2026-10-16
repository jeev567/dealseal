package com.dealseal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgreementAnalysisValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgreementAnalysisValidator validator = new AgreementAnalysisValidator();

    private ObjectNode valid() throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"agreementStatus":"NEEDS_CLARIFICATION","readyForSignature":false,
             "terms":[{"field":"Price","value":"$100","status":"PROPOSED",
             "evidenceMessageIds":["m1"],"question":"Confirm price?"}],"warnings":["Review required"]}
            """);
    }

    @Test void acceptsValidResponse() throws Exception {
        assertDoesNotThrow(() -> validator.validate(valid(), Set.of("m1")));
    }

    @Test void rejectsUnsupportedSignatureReadiness() throws Exception {
        ObjectNode response = valid();
        response.put("readyForSignature", true);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(response, Set.of("m1")));
    }

    @Test void rejectsInventedEvidence() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(valid(), Set.of("m2")));
    }

    @Test void rejectsInvalidStatus() throws Exception {
        ObjectNode response = valid();
        response.put("agreementStatus", "SIGNED");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(response, Set.of("m1")));
    }

    @Test void rejectsMissingRequiredFields() throws Exception {
        ObjectNode response = valid();
        response.remove("warnings");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(response, Set.of("m1")));
    }
}
