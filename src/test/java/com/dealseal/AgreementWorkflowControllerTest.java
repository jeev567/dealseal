package com.dealseal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgreementWorkflowControllerTest {
    private final AgreementApprovalController approvals = new AgreementApprovalController();
    private final AgreementWorkflowController workflow =
            new AgreementWorkflowController(new AgreementDraftController(), approvals, new AgreementCompletenessValidator());

    private AgreementDraftController.Term term(String status) {
        return new AgreementDraftController.Term("Price", "$2300", status, List.of("m1", "m2"), "");
    }

    @Test void createsVersionedAgreementFromConfirmedTerms() {
        var result = workflow.fromAnalysis(new AgreementWorkflowController.FromAnalysisRequest(
                List.of(term("CONFIRMED_IN_CHAT"),
                        new AgreementDraftController.Term("Delivery Date", "November 20, 2026", "CONFIRMED_IN_CHAT", List.of("m2"), ""),
                        new AgreementDraftController.Term("Scope of Work", "Five page website", "CONFIRMED_IN_CHAT", List.of("m3"), ""),
                        new AgreementDraftController.Term("Revisions", "Two rounds", "CONFIRMED_IN_CHAT", List.of("m4"), ""),
                        new AgreementDraftController.Term("Payment Schedule", "Half upfront", "CONFIRMED_IN_CHAT", List.of("m5"), ""),
                        new AgreementDraftController.Term("Acceptance Criteria", "Client approves after testing", "CONFIRMED_IN_CHAT", List.of("m6"), ""),
                        new AgreementDraftController.Term("IP Ownership", "Client owns code after payment", "CONFIRMED_IN_CHAT", List.of("m7"), ""))));
        assertEquals(1, result.agreement().version());
        assertFalse(result.agreement().bothApproved());
        assertEquals("DRAFT_REQUIRES_REVIEW", result.draft().status());
        assertTrue(result.agreement().terms().getFirst().contains("$2300"));
        var client = approvals.approve(result.agreement().id(),
                new AgreementApprovalController.ApproveRequest("CLIENT", 1));
        assertFalse(client.bothApproved());
        var both = approvals.approve(result.agreement().id(),
                new AgreementApprovalController.ApproveRequest("DEVELOPER", 1));
        assertTrue(both.bothApproved());
    }

    @Test void blocksUnresolvedTerms() {
        var error = assertThrows(ResponseStatusException.class,
                () -> workflow.fromAnalysis(new AgreementWorkflowController.FromAnalysisRequest(
                        List.of(term("CONFIRMED_IN_CHAT"), term("PROPOSED")))));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatusCode());
    }

    @Test void blocksMalformedTerms() {
        assertThrows(ResponseStatusException.class,
                () -> workflow.fromAnalysis(new AgreementWorkflowController.FromAnalysisRequest(
                        List.of(term("UNSUPPORTED")))));
    }
}
