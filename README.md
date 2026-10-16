# DealSeal AI

**From conversation to clarity. From agreement to confident checkout.**

[![Live Demo](https://img.shields.io/badge/Live%20Demo-DealSeal%20AI-2563eb)](https://dealseal-demo.duckdns.org)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-6DB33F)
![React](https://img.shields.io/badge/React-19-149ECA)
![PayPal](https://img.shields.io/badge/PayPal-Sandbox-003087)

**Live demo:** https://dealseal-demo.duckdns.org  
**Status:** Hackathon prototype — PayPal Sandbox only

> DealSeal AI is an agreement-readiness workspace that converts informal client–provider negotiations into structured terms, highlights missing commitments, records explicit demo consent to a locked agreement version, and connects the result to PayPal Sandbox checkout.

## The story

A client asks a freelancer to build a website. They negotiate a price, delivery date, revisions, and an upfront payment in chat. It feels like a deal — but who owns the finished code? What exactly counts as delivery? How does the client accept the work?

These omissions often become visible only *after* money changes hands.

DealSeal starts with the conversation the parties already had, identifies what they actually agreed to, asks about what they did not, and guides them toward a shared, reviewable agreement **before checkout**.

## Why DealSeal?

| Common approach | Where DealSeal fits |
| --- | --- |
| Payment link | Adds structured agreement review before payment |
| Contract template | Starts with the actual negotiation rather than a blank form |
| AI summary | Distinguishes confirmed, proposed, conflicted, and missing terms |
| E-signature workflow | Helps resolve incomplete terms before the final document is approved |

These are category-level distinctions, not claims that competing products lack every listed capability.

## Features

- **AI-assisted term extraction** from a pasted conversation.
- **Seven required terms:** price, delivery date, scope of work, revisions, payment schedule, acceptance criteria, and IP ownership.
- **Explicit status labels:** `CONFIRMED_IN_CHAT`, `PROPOSED`, `CONFLICTED`, and `MISSING`.
- **Gap resolution:** record responses from both parties and reanalyze the updated conversation.
- **Versioned agreement review** with demo approvals.
- **Locked consent workflow:** separate review links for client and developer, explicit approval, and a SHA-256 fingerprint for the exact version.
- **Unified PDF:** issue, download, and verify a finalized agreement/consent PDF.
- **PayPal Sandbox checkout:** create an order, approve in PayPal, and capture the payment.

**Important:** Demo consent links are bearer credentials, not identity verification or legally certified e-signatures. PDF hash verification checks file equality, not legal validity. DealSeal is not an escrow service.

## How it works

```text
Client / developer conversation
             |
             v
       AI term analysis
             |
             v
 Confirmed / proposed / missing / conflicted
             |
             v
    Resolve gaps and reanalyze
             |
             v
     Create versioned agreement
             |
             v
   Lock terms + SHA-256 fingerprint
             |
             v
 Client consent + developer consent
             |
             +----> Unified PDF + verification
             |
             v
       PayPal Sandbox order
             |
             v
   Buyer approval -> order capture
```

### Demo scenario

The bundled example starts with a website project negotiated at **$2,300**, delivery on **November 20**, **two revisions**, and **$1,150 upfront / $1,150 after delivery**. The analyzer can flag unresolved details such as scope, acceptance criteria, and IP ownership so the parties can resolve them before proceeding.

### Complete sample conversation

**[View the complete sample conversation](docs/sample-conversation.md)** — a full website-project negotiation covering all seven required agreement terms.

## Why it matters to PayPal

DealSeal explores an **agreement-readiness layer** around payments, particularly for freelancers, agencies, and service providers:

- Help merchants and buyers see the same scope, milestones, and payment expectations.
- Potentially reduce preventable misunderstandings by making ambiguous terms visible before checkout.
- Connect an agreement reference and consent evidence to a payment workflow.
- Offer a natural journey from negotiation to PayPal checkout.

These are **product hypotheses**, not measured improvements to PayPal dispute rates or conversion. A real pilot should measure agreement completion, checkout conversion, user clarity, and downstream disputes.

## Tech stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, Vite 6, lucide-react |
| Backend | Java 21, Spring Boot 3.5.6, Gradle |
| AI | OpenAI-backed analysis via Spring Boot |
| Database | SQLite, Spring JDBC |
| PDF | Apache PDFBox 3.0.6; SHA-256 comparison |
| Payments | PayPal REST APIs, Sandbox Orders v2 |
| Hosting | AWS EC2 (Amazon Linux 2023), Nginx, systemd |
| Deployment | Private S3 artifact transfer + AWS Systems Manager (SSM) |
| DNS / TLS | DuckDNS + Let's Encrypt HTTPS |

### Architecture

```text
React / Vite SPA
      |
   HTTPS (Nginx)
      |
 Spring Boot REST API
      |---- OpenAI (term analysis)
      |---- SQLite (agreements / demo state)
      |---- PDFBox (document issuance)
      |---- PayPal Sandbox (order + capture)
```

## PayPal Developer integration

1. Sign in to the [PayPal Developer Dashboard](https://developer.paypal.com/dashboard/).
2. Create or select a **Sandbox REST API app** under **Apps & Credentials**.
3. Obtain the app's **Client ID** and **Client Secret**. These identify and authenticate the API integration; they are **not** the Sandbox buyer login.
4. Use a **Sandbox Business account** as the test merchant and a **Sandbox Personal account** as the test buyer.
5. Configure the backend with credentials through private server-side environment settings; **never commit secrets**.
6. After both parties consent to the locked DealSeal version, create a PayPal order, open the PayPal approval URL, and capture the order after buyer approval.

### Application endpoints used by the frontend

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/ai/analyze` | Analyze conversation and terms |
| `POST` | `/api/agreements/negotiation/continue` | Record answers and continue negotiation |
| `POST` | `/api/agreements/from-analysis` | Create agreement from confirmed terms |
| `POST` | `/api/agreements/{id}/approvals` | Record a demo approval |
| `POST` | `/api/locked-consent/lock` | Lock agreement version and create review links |
| `GET` | `/api/locked-consent/{id}` | Check independent consent status |
| `GET` | `/api/locked-consent/{id}/review?token=...` | Review locked version via private link |
| `POST` | `/api/locked-consent/{id}/consent?token=...` | Record explicit link-based consent |
| `POST` | `/api/finalized-contracts/issue` | Issue unified agreement/consent PDF |
| `POST` | `/api/finalized-contracts/{id}/verify` | Verify an uploaded PDF |
| `POST` | `/api/paypal/sandbox/orders` | Create PayPal Sandbox order |
| `POST` | `/api/paypal/sandbox/orders/{orderId}/capture` | Capture PayPal Sandbox order |

The table lists routes confirmed in the React client, not a complete generated backend API inventory.

### PayPal REST API (standard Orders v2 flow)

| Method | PayPal Sandbox endpoint | Purpose |
| --- | --- | --- |
| `POST` | `https://api-m.sandbox.paypal.com/v1/oauth2/token` | Exchange Client ID / Secret for OAuth access token |
| `POST` | `https://api-m.sandbox.paypal.com/v2/checkout/orders` | Create checkout order |
| `POST` | `https://api-m.sandbox.paypal.com/v2/checkout/orders/{id}/capture` | Capture approved order |

The external endpoints above describe the standard PayPal REST flow. Exact backend HTTP implementation details should be checked in the relevant service class before treating this as a request-level trace.

**Identifiers:** PayPal Client ID identifies the REST app; Client Secret authenticates it; OAuth access token authorizes API requests; PayPal Order ID identifies a checkout order; DealSeal Agreement ID and version identify the agreed terms. These are separate identifiers with different roles.

Reference: [PayPal REST API documentation](https://developer.paypal.com/api/rest/) · [Orders API](https://developer.paypal.com/docs/api/orders/v2/).

## Run locally

### Prerequisites

- JDK 21
- Node.js and npm
- Access to the required backend environment configuration for OpenAI, PayPal Sandbox, and persistence

> The repository does not currently provide a verified, exhaustive environment-variable template. Configure backend credentials and data paths according to your local setup. Do not place secrets in `frontend/` or commit them to Git.

**Backend** (repository root):

```bash
./gradlew bootRun
```

**Frontend** (separate terminal):

```bash
cd frontend
npm ci
npm run dev
```

Vite proxies `/api` to `http://localhost:8080`. Open the local URL printed by Vite.

**Tests and production builds:**

```bash
./gradlew test
./gradlew clean bootJar
cd frontend && npm ci && npm run build
```

## Deployment

The live hackathon instance uses **EC2 + Nginx + systemd**, with a one-command deployment script.

Prerequisites on the operator's machine: AWS CLI with an authorized `dealseal-admin` profile, Java 21, Node/npm, Python 3, and Gradle wrapper dependencies. The existing EC2 instance must be configured for SSM and have the DealSeal service installed.

```bash
git pull origin main
bash infra/deploy.sh
```

The script builds both applications, uploads temporary artifacts to a private S3 bucket, issues an SSM deployment command, restarts Spring Boot, checks basic backend availability, triggers one DuckDNS refresh, and cleans up uploaded artifacts. It keeps a previous application-file version for rollback on health-check failure.

**Caveats:** This is a single-instance hackathon deployment, not a zero-downtime production release. The rollback does not revert database migrations. The health check is a basic availability check rather than a full end-to-end checkout test.

Relevant infrastructure: `infra/terraform/` and `infra/deploy.sh`.

## Security and limitations

- **Sandbox only:** no real PayPal payments are processed by the demo.
- **AI review required:** extracted terms can be incomplete or incorrect; parties must read the final text.
- **Consent ≠ verified identity:** private approval links prove possession of a link, not the person's identity.
- **Not legal advice:** the output is not a substitute for legal review or a certified signing workflow.
- **Hash verification ≠ signature:** matching a stored PDF hash does not authenticate a signer.
- **Protect data:** negotiation transcripts are sent to the configured backend AI provider. Avoid submitting sensitive information.
- **Server-side secrets:** keep API credentials and tokens outside the repository and client bundle.

## Roadmap

- Identity-bound approvals and stronger audit trail
- Expiring / revocable consent links
- PayPal production integration, webhooks, idempotency, and reconciliation
- Milestone payments and delivery acceptance workflows
- Privacy, security, and legal/compliance review
- Merchant pilots measuring conversion and dispute outcomes

---

**DealSeal AI** — built as a PayPal hackathon prototype to explore a simple idea: *better payment experiences start with clearer agreements.*
