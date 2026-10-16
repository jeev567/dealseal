package com.dealseal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEMO ONLY. Bearer approval links prove possession of a link, not identity.
 * State is in-memory; restart clears locks. Do not use for legal signatures or production payments.
 */
@RestController
@RequestMapping("/api/locked-consent")
public class LockedConsentController {
    public record LockRequest(UUID agreementId, int version) {}
    public record LockView(UUID agreementId, int version, String sha256, String status,
                           boolean clientApproved, boolean developerApproved,
                           String clientLink, String developerLink) {}
    public record ConsentView(UUID agreementId, int version, String sha256, String party,
                              List<String> terms, boolean approved, String status) {}
    public record ConsentRequest(boolean explicitlyAgree) {}
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final class Locked {
        final UUID id;
        final int version;
        final String hash;
        final List<String> terms;
        final String clientToken;
        final String developerToken;
        boolean clientApproved;
        boolean developerApproved;
        Locked(AgreementApprovalController.AgreementView agreement) {
            id = agreement.id(); version = agreement.version(); terms = List.copyOf(agreement.terms());
            hash = sha(String.join("\n", terms).getBytes(StandardCharsets.UTF_8));
            clientToken = token(); developerToken = token();
        }
    }
    private final AgreementApprovalController agreements;
    private final Map<UUID, Locked> locks = new ConcurrentHashMap<>();
    public LockedConsentController(AgreementApprovalController agreements) { this.agreements = agreements; }
    private static String token() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private Locked current(UUID id) {
        Locked locked = locks.get(id);
        if (locked == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No locked contract for this agreement");
        var latest = agreements.read(id);
        if (latest.version() != locked.version || !latest.terms().equals(locked.terms))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agreement changed; create a new lock and obtain new approvals");
        return locked;
    }
    private static String status(Locked l) { return l.clientApproved && l.developerApproved ? "BOTH_CONSENTED_DEMO" : "AWAITING_INDEPENDENT_CONSENT"; }
    private static LockView view(Locked l, boolean includeLinks) {
        return new LockView(l.id, l.version, l.hash, status(l), l.clientApproved, l.developerApproved,
            includeLinks ? "/?consent=" + l.id + "&token=" + l.clientToken : null,
            includeLinks ? "/?consent=" + l.id + "&token=" + l.developerToken : null);
    }
    @PostMapping("/lock")
    public LockView lock(@RequestBody LockRequest request) {
        if (request == null || request.agreementId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agreement required");
        var agreement = agreements.read(request.agreementId());
        if (agreement.version() != request.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Agreement version changed");
        synchronized (locks) {
            Locked existing = locks.get(agreement.id());
            if (existing != null && existing.version == agreement.version() && existing.terms.equals(agreement.terms()))
                return view(existing, true);
            Locked locked = new Locked(agreement);
            locks.put(agreement.id(), locked);
            return view(locked, true);
        }
    }
    @GetMapping("/{id}")
    public LockView read(@PathVariable UUID id) { Locked l = current(id); synchronized(l) { return view(l, false); } }
    private String party(Locked l, String token) {
        if (token == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Approval link required");
        byte[] candidate = token.getBytes(StandardCharsets.US_ASCII);
        if (MessageDigest.isEqual(candidate, l.clientToken.getBytes(StandardCharsets.US_ASCII))) return "CLIENT";
        if (MessageDigest.isEqual(candidate, l.developerToken.getBytes(StandardCharsets.US_ASCII))) return "DEVELOPER";
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid approval link");
    }
    @GetMapping("/{id}/review")
    public ConsentView review(@PathVariable UUID id, @RequestParam String token) {
        Locked l = current(id); synchronized(l) {
            String p = party(l, token);
            return new ConsentView(id, l.version, l.hash, p, l.terms,
                    p.equals("CLIENT") ? l.clientApproved : l.developerApproved, status(l));
        }
    }
    @PostMapping("/{id}/consent")
    public ConsentView consent(@PathVariable UUID id, @RequestParam String token, @RequestBody ConsentRequest request) {
        if (request == null || !request.explicitlyAgree())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explicit consent required");
        Locked l = current(id); synchronized(l) {
            String p = party(l, token);
            if (p.equals("CLIENT")) l.clientApproved = true; else l.developerApproved = true;
            return new ConsentView(id, l.version, l.hash, p, l.terms, true, status(l));
        }
    }
    public void requireBoth(UUID id, int version) {
        Locked l = current(id); synchronized(l) {
            if (l.version != version || !l.clientApproved || !l.developerApproved)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Both independent demo consents on the locked version are required");
        }
    }
}
