package com.dealseal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * Converts an already analyzed conversation into a reviewable, versioned demo agreement.
 * AI analysis is advisory: explicit approvals are still required.
 */
@RestController
@RequestMapping("/api/agreements")
public class AgreementWorkflowController {
    public record FromAnalysisRequest(@NotNull List<AgreementDraftController.Term> terms) {}
    public record FromAnalysisResponse(AgreementApprovalController.AgreementView agreement,
                                       AgreementDraftController.DraftResponse draft) {}

    private final AgreementDraftController drafts;
    private final AgreementApprovalController approvals;
    private final AgreementCompletenessValidator completeness;

    public AgreementWorkflowController(AgreementDraftController drafts, AgreementApprovalController approvals,
                                       AgreementCompletenessValidator completeness) {
        this.drafts = drafts;
        this.approvals = approvals;
        this.completeness = completeness;
    }

    @PostMapping("/from-analysis")
    public FromAnalysisResponse fromAnalysis(@Valid @RequestBody FromAnalysisRequest request) {
        var draft = drafts.draft(new AgreementDraftController.DraftRequest(request.terms()));
        // Only terms confirmed in the conversation are eligible for the approval workflow.
        // Other terms remain open questions in the draft, and block agreement creation.
        if (request.terms().stream().anyMatch(t -> !"CONFIRMED_IN_CHAT".equals(t.status()))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Resolve proposed, missing or conflicted terms before requesting approvals");
        }
        completeness.requireComplete(request.terms());
        List<String> clauses = draft.sections().stream()
                .map(s -> s.heading() + ": " + s.text())
                .toList();
        var agreement = approvals.create(new AgreementApprovalController.CreateRequest(clauses));
        return new FromAnalysisResponse(agreement, draft);
    }
}
