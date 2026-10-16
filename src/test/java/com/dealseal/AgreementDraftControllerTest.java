package com.dealseal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgreementDraftControllerTest {
    private final AgreementDraftController controller = new AgreementDraftController();

    @Test void confirmedTermStillRequiresReview() {
        var response = controller.draft(new AgreementDraftController.DraftRequest(List.of(
                new AgreementDraftController.Term("Price", "$100", "CONFIRMED_IN_CHAT", List.of("m1"), ""))));
        assertFalse(response.readyForSignature());
        assertEquals("DRAFT_REQUIRES_REVIEW", response.status());
        assertEquals(List.of("m1"), response.sections().get(0).evidenceMessageIds());
    }

    @Test void missingTermCreatesPlaceholderAndQuestion() {
        var response = controller.draft(new AgreementDraftController.DraftRequest(List.of(
                new AgreementDraftController.Term("Delivery", "", "MISSING", List.of(), "When?"))));
        assertTrue(response.sections().get(0).text().contains("[TO BE AGREED]"));
        assertEquals(List.of("When?"), response.openQuestions());
    }

    @Test void rejectsInvalidStatus() {
        var error = assertThrows(ResponseStatusException.class, () -> controller.draft(
                new AgreementDraftController.DraftRequest(List.of(
                        new AgreementDraftController.Term("Price", "$100", "SIGNED", List.of(), "")))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test void rejectsEmptyTerms() {
        assertThrows(ResponseStatusException.class, () -> controller.draft(
                new AgreementDraftController.DraftRequest(List.of())));
    }
}
