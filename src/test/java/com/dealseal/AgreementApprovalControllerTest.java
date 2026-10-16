package com.dealseal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgreementApprovalControllerTest {
    private final AgreementApprovalController controller = new AgreementApprovalController();

    private UUID create() {
        return controller.create(new AgreementApprovalController.CreateRequest(List.of("Price: $2300"))).id();
    }

    @Test void requiresBothPartiesOnSameVersion() {
        UUID id = create();
        var client = controller.approve(id, new AgreementApprovalController.ApproveRequest("CLIENT", 1));
        assertFalse(client.bothApproved());
        var both = controller.approve(id, new AgreementApprovalController.ApproveRequest("DEVELOPER", 1));
        assertTrue(both.bothApproved());
        assertEquals("BOTH_APPROVED_IN_DEMO", both.status());
    }

    @Test void revisionResetsApprovalsAndRejectsStaleApproval() {
        UUID id = create();
        controller.approve(id, new AgreementApprovalController.ApproveRequest("CLIENT", 1));
        var revised = controller.revise(id, new AgreementApprovalController.ReviseRequest(1, List.of("Price: $2400")));
        assertEquals(2, revised.version());
        assertFalse(revised.clientApproved());
        assertFalse(revised.developerApproved());
        var error = assertThrows(ResponseStatusException.class,
                () -> controller.approve(id, new AgreementApprovalController.ApproveRequest("DEVELOPER", 1)));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test void rejectsStaleRevision() {
        UUID id = create();
        var error = assertThrows(ResponseStatusException.class,
                () -> controller.revise(id, new AgreementApprovalController.ReviseRequest(2, List.of("Changed"))));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test void rejectsUnknownParty() {
        UUID id = create();
        assertThrows(ResponseStatusException.class,
                () -> controller.approve(id, new AgreementApprovalController.ApproveRequest("OTHER", 1)));
    }

    @Test void rejectsMissingTerms() {
        assertThrows(ResponseStatusException.class,
                () -> controller.create(new AgreementApprovalController.CreateRequest(List.of())));
    }
}
