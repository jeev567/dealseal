# Contract PDF prototype (unsigned)

This branch adds a **demo-only unsigned PDF** and SHA-256 integrity verification. It does **not** implement legal e-signatures, cryptographic PDF signatures, identity checks, two-party approval, or an escrow mechanism.

## Run

Start Spring Boot as usual and open the web UI. Analyze a conversation, then select **Generate unsigned PDF**. Download the document, and upload it using **Verify PDF**. The backend compares the uploaded file's SHA-256 digest with the original file retained on the server.

## API

- `POST /api/contracts/issue` — JSON: `{"buyer":"Client","seller":"Developer","terms":[{"field":"Price","value":"$2,300"}]}`. Returns contract ID, SHA-256 hash, and download URL.
- `GET /api/contracts/{id}/download` — original PDF.
- `POST /api/contracts/{id}/verify` — multipart form file field named `file`; returns `MATCH` or `MODIFIED_OR_DIFFERENT`.

Original PDF bytes are stored in `./data/contracts` by default. Configure `dealseal.contracts.directory` to change it. **Exclude this directory from Git.**

## Security limitations before production

The endpoints have no authentication or authorization. Anyone knowing a contract ID could download its contents; UUIDs are not access control. Add authentication, per-party access control, rate limiting, and malware/file-type validation. Use encrypted, durable, versioned object storage with retention/immutability controls and independent hash/audit records. File-system admins can currently replace stored originals, so this prototype does **not** protect against server-side tampering. The hash comparison only proves the uploaded bytes match the *current* server copy.

The PDF uses basic Helvetica with ASCII fallback; international characters are replaced with `?`. Add embedded Unicode fonts before production. Contracts may span multiple pages.

Do not allow PayPal capture solely because a draft PDF was issued. Require verified identities, independent consent to a frozen contract version, and proper e-signature and payment-state validation.
