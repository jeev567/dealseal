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
