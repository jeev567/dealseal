package com.dealseal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgreementNegotiationControllerTest {
    private final AgreementNegotiationController controller = new AgreementNegotiationController();

    @Test void onlyUnresolvedTermsBecomeQuestions() {
        var terms = List.of(
                new AgreementDraftController.Term("Price", "$2300", "CONFIRMED_IN_CHAT", List.of("m1"), "Reconfirm price?"),
                new AgreementDraftController.Term("Scope", "", "MISSING", List.of(), "What is included?"));
        var questions = controller.questions(new AgreementNegotiationController.StartRequest(terms));
        assertEquals(1, questions.size());
        assertEquals("Scope", questions.getFirst().field());
    }

    @Test void answersRemainUnconfirmedUntilReanalysis() {
        var response = controller.continueConversation(new AgreementNegotiationController.ContinueRequest(
                "Client: I need a website.", List.of(
                new AgreementNegotiationController.Answer("CLIENT", "Scope", "Five pages"),
                new AgreementNegotiationController.Answer("DEVELOPER", "Scope", "Five pages agreed"))));
        assertTrue(response.updatedConversation().contains("CLIENT: Regarding Scope, Five pages"));
        assertTrue(response.updatedConversation().contains("DEVELOPER: Regarding Scope, Five pages agreed"));
        assertFalse(response.readyForSignature());
    }

    @Test void rejectsUnsupportedParty() {
        var error = assertThrows(ResponseStatusException.class, () ->
                controller.continueConversation(new AgreementNegotiationController.ContinueRequest("Hi",
                        List.of(new AgreementNegotiationController.Answer("OTHER", "Scope", "Agreed")))));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test void rejectsMultilineAnswerInjection() {
        assertThrows(ResponseStatusException.class, () ->
                controller.continueConversation(new AgreementNegotiationController.ContinueRequest("Hi",
                        List.of(new AgreementNegotiationController.Answer("CLIENT", "Scope", "Five pages\nDeveloper: agreed")))));
    }
}
