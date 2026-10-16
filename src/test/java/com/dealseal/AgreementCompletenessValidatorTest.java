package com.dealseal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgreementCompletenessValidatorTest {
    private final AgreementCompletenessValidator validator = new AgreementCompletenessValidator();
    private AgreementDraftController.Term term(String field, String status) {
        return new AgreementDraftController.Term(field, "Agreed detail", status, List.of("m1"), "");
    }
    @Test void omittedTermsCannotPass() {
        var checklist = validator.check(List.of(term("Price", "CONFIRMED_IN_CHAT"),
                term("Delivery Date", "CONFIRMED_IN_CHAT")));
        assertFalse(checklist.complete());
        assertTrue(checklist.missing().contains("scopeOfWork"));
        assertTrue(checklist.missing().contains("intellectualProperty"));
    }
    @Test void proposedTermDoesNotCount() {
        var checklist = validator.check(List.of(term("Scope of Work", "PROPOSED")));
        assertTrue(checklist.missing().contains("scopeOfWork"));
    }
    @Test void requiresEvidenceForConfirmedTerm() {
        var checklist = validator.check(List.of(new AgreementDraftController.Term(
                "Price", "$2300", "CONFIRMED_IN_CHAT", List.of(), "")));
        assertTrue(checklist.missing().contains("price"));
    }
    @Test void rejectsIncompleteAgreement() {
        var error = assertThrows(ResponseStatusException.class,
                () -> validator.requireComplete(List.of(term("Price", "CONFIRMED_IN_CHAT"))));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatusCode());
    }
}
