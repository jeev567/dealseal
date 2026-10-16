package com.dealseal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hackathon prototype only: state is in memory and roles are self-asserted.
 * Do not use as legal signatures, identity verification, or payment authorization.
 */
@RestController
@RequestMapping("/api/agreements")
public class AgreementApprovalController {
    public record CreateRequest(@NotEmpty List<@NotBlank String> terms) {}
    public record ApproveRequest(@NotBlank String party, int version) {}
    public record ReviseRequest(int expectedVersion, @NotEmpty List<@NotBlank String> terms) {}
    public record AgreementView(UUID id, int version, List<String> terms,
                                boolean clientApproved, boolean developerApproved,
                                boolean bothApproved, String status, Instant updatedAt) {}

    private static final class Agreement {
        final UUID id;
        int version = 1;
        List<String> terms;
        boolean clientApproved;
        boolean developerApproved;
        Instant updatedAt = Instant.now();

        Agreement(UUID id, List<String> terms) {
            this.id = id;
            this.terms = terms;
        }

        AgreementView view() {
            boolean complete = clientApproved && developerApproved;
            return new AgreementView(id, version, terms, clientApproved, developerApproved,
                    complete, complete ? "BOTH_APPROVED_IN_DEMO" : "AWAITING_APPROVAL", updatedAt);
        }
    }

    private final Map<UUID, Agreement> agreements = new ConcurrentHashMap<>();

    private List<String> validateTerms(List<String> terms) {
        if (terms == null || terms.isEmpty() || terms.size() > 30
                || terms.stream().anyMatch(t -> t == null || t.isBlank() || t.length() > 2000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide 1 to 30 nonblank terms, max 2000 characters each");
        }
        return List.copyOf(terms);
    }

    private Agreement get(UUID id) {
        Agreement agreement = agreements.get(id);
        if (agreement == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agreement not found");
        return agreement;
    }

    @PostMapping
    public AgreementView create(@Valid @RequestBody CreateRequest request) {
        List<String> terms = validateTerms(request.terms());
        UUID id = UUID.randomUUID();
        Agreement agreement = new Agreement(id, terms);
        agreements.put(id, agreement);
        return agreement.view();
    }

    @GetMapping("/{id}")
    public AgreementView read(@PathVariable UUID id) {
        Agreement agreement = get(id);
        synchronized (agreement) {
            return agreement.view();
        }
    }

    @PutMapping("/{id}/versions")
    public AgreementView revise(@PathVariable UUID id, @Valid @RequestBody ReviseRequest request) {
        List<String> terms = validateTerms(request.terms());
        Agreement agreement = get(id);
        synchronized (agreement) {
            if (request.expectedVersion() != agreement.version)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Agreement version changed; review the latest version");
            if (agreement.version == Integer.MAX_VALUE)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Version limit reached");
            agreement.version++;
            agreement.terms = terms;
            agreement.clientApproved = false;
            agreement.developerApproved = false;
            agreement.updatedAt = Instant.now();
            return agreement.view();
        }
    }

    @PostMapping("/{id}/approvals")
    public AgreementView approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest request) {
        Agreement agreement = get(id);
        synchronized (agreement) {
            if (request.version() != agreement.version)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Agreement version changed; review the latest version");
            switch (request.party().toUpperCase(Locale.ROOT)) {
                case "CLIENT" -> agreement.clientApproved = true;
                case "DEVELOPER" -> agreement.developerApproved = true;
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Party must be CLIENT or DEVELOPER");
            }
            agreement.updatedAt = Instant.now();
            return agreement.view();
        }
    }
}
