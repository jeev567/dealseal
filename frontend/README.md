# DealSeal AI frontend (v1.0 demo)

Run the Spring Boot backend on localhost:8080, with an OpenAI key configured on the backend only.

```bash
cd frontend
npm install
npm run dev
```

Open the URL printed by Vite (usually http://localhost:5173). Vite proxies /api to Spring Boot on localhost:8080. No API key is needed in the browser. Build with `npm run build`.

Flow: analyze conversation → review seven terms → answer open questions as both parties → reanalyze → create versioned demo agreement → approve each role.

**Demo limitations:** The backend holds agreements in memory and accepts self-asserted party roles; these approvals are not signatures, verified identity, enforceable contracts, or PayPal payment authorization. A user can enter both parties' responses and approvals; do not use for real agreements. There is no authentication, persistent storage, or PayPal integration yet. The seven-term checklist currently applies to website projects only.

## PayPal Sandbox v1.1

Configure Spring Boot locally using environment variables or a git-ignored local configuration file:

```yaml
paypal:
  sandbox:
    client-id: ${PAYPAL_SANDBOX_CLIENT_ID:}
    client-secret: ${PAYPAL_SANDBOX_CLIENT_SECRET:}
```

Get Sandbox REST API app credentials and Sandbox buyer login from https://developer.paypal.com/dashboard/ . Never commit the client secret. Restart Spring Boot after configuration.

After both demo approvals, enter the payment amount (e.g., `1150.00` for the upfront installment), create a Sandbox order, open its approval URL, approve as a Sandbox buyer, return to DealSeal and explicitly click **Capture approved Sandbox payment**. The backend checks order approval and the stored agreement version before capture. The amount is manually entered because payment milestones are not yet represented as structured fields.

**Security:** Sandbox-only; no live API endpoint. No authenticated buyer/seller identities, database, webhook reconciliation, or production-ready idempotent order ledger. Agreement roles are self-asserted. Do not use for real money. Restarting the backend loses order mappings.
