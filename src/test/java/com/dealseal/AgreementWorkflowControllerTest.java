package com.dealseal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgreementWorkflowControllerTest {
    private final AgreementApprovalController approvals = new AgreementApprovalController();
    private final AgreementWorkflowController workflow =
            new AgreementWorkflowController(new AgreementDraftController(), approvals);

    private AgreementDraftController.Term term(String status) {
        return new AgreementDraftController.Term("Price", "$2300", status, List.of("m1", "m2"), "");
    }

    @Test void createsVersionedAgreementFromConfirmedTerms() {
        var result = workflow.fromAnalysis(new AgreementWorkflowController.FromAnalysisRequest(
                List.of(term("CONFIRMED_IN_CHAT"))));
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
