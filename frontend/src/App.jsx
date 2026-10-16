import React, { useMemo, useState } from 'react';
import { ArrowRight, Check, CheckCircle2, ChevronRight, CircleAlert, ClipboardList, FileText, LockKeyhole, MessageSquareText, RefreshCw, ShieldCheck, Sparkles, Wallet, X } from 'lucide-react';

const EXAMPLE = `Client: I need a website for $2,000, delivered November 15.
Developer: I can do $2,300, delivered November 20.
Client: $2,300 works. Two revisions included?
Developer: Yes, two revisions included.
Client: I agree to the November 20 delivery date.
Developer: Confirmed. I will deliver the website on November 20.
Client: I will pay $1,150 upfront and $1,150 after delivery.
Developer: Agreed to the payment schedule.
CLIENT: Regarding Scope of Work, The website must include five responsive pages: Home, About, Services, Contact, and FAQ. It must work on desktop and mobile devices. The scope includes frontend development, a contact form, basic SEO setup, and deployment. Hosting fees and ongoing maintenance are excluded.
DEVELOPER: Regarding Scope of Work, I agree to develop and deliver the five responsive pages, contact form, basic SEO setup, and deployment as described. Hosting fees and ongoing maintenance are not included in the $2,300 price.
CLIENT: Regarding Acceptance Criteria, I will accept the website when all five agreed pages are delivered, the contact form works correctly, the website displays properly on desktop and mobile, and there are no critical bugs. I will review the website within five business days of delivery and provide written acceptance or a list of issues.
DEVELOPER: Regarding Acceptance Criteria, I agree to these acceptance criteria. I will deliver a working website meeting the agreed requirements, fix any reported critical bugs within the agreed scope, and request written acceptance after the client's review.
CLIENT: Regarding IP Ownership, I will own the intellectual property rights to the custom website code, design, and deliverables created specifically for this project after full payment of $2,300. Third-party libraries, fonts, and licensed assets remain subject to their original licenses.
DEVELOPER: Regarding IP Ownership, I agree to transfer ownership of the custom website code, design, and project deliverables to the client after receiving the full $2,300 payment. Pre-existing tools, reusable components, and third-party assets are excluded from this transfer and remain subject to their applicable licenses.`;
const REQUIRED = ['Price', 'Delivery Date', 'Scope of Work', 'Revisions', 'Payment Schedule', 'Acceptance Criteria', 'IP Ownership'];
const STEPS = ['Conversation', 'Review terms', 'Resolve gaps', 'Approve'];
const STATUS = { CONFIRMED_IN_CHAT: 'Confirmed in chat', PROPOSED: 'Proposed', CONFLICTED: 'Conflicted', MISSING: 'Missing' };

async function api(path, body) {
  let response;
  try {
    response = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  } catch {
    throw new Error('Cannot reach the backend. Start Spring Boot on port 8080.');
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.detail || data.message || data.error || `Request failed (HTTP ${response.status})`);
  return data;
}

function Logo() {
  return <div className="brand"><div className="brand-icon"><ShieldCheck size={21} strokeWidth={2.2}/></div><span>DealSeal<span className="brand-ai"> AI</span></span><span className="brand-beta">BETA</span></div>;
}

export default function App() {
  const [consentRoute] = useState(() => { const p = new URLSearchParams(window.location.search); return p.has('consent') && p.has('token') ? {id:p.get('consent'),token:p.get('token')} : null; });
  const [paypalReturn] = useState(() => {
    const params = new URLSearchParams(window.location.search);
    return params.has('paypalReturn') ? {status:params.get('paypalReturn'),orderId:params.get('token')} : null;
  });
  const [returnPayment, setReturnPayment] = useState(null);
  const [returnBusy, setReturnBusy] = useState(false);
  const [returnError, setReturnError] = useState('');
  const completeReturnedPayment = async () => {
    if (!paypalReturn?.orderId) {setReturnError('PayPal did not provide an order ID.');return;}
    setReturnBusy(true);setReturnError('');
    try {
      const result = await api(`/api/paypal/sandbox/orders/${encodeURIComponent(paypalReturn.orderId)}/capture`, {});
      setReturnPayment(result);
    } catch (e) {setReturnError(e.message);} finally {setReturnBusy(false);}
  };
  React.useEffect(() => {
    if (paypalReturn?.status === 'approved' && paypalReturn.orderId) completeReturnedPayment();
  }, []);
  const [consentError, setConsentError] = useState('');
  const [consentBusy, setConsentBusy] = useState(false);
  const [explicitConsent, setExplicitConsent] = useState(false);
  React.useEffect(() => {
    if (!consentRoute) return;
    fetch(`/api/locked-consent/${encodeURIComponent(consentRoute.id)}/review?token=${encodeURIComponent(consentRoute.token)}`)
      .then(async r => {const d=await r.json();if(!r.ok)throw Error(d.detail||d.message||'Review link is invalid or expired');return d;})
      .then(setConsentView).catch(e=>setConsentError(e.message));
  }, [consentRoute]);
  const submitIndependentConsent = async () => {
    setConsentBusy(true);setConsentError('');
    try {
      const r=await fetch(`/api/locked-consent/${encodeURIComponent(consentRoute.id)}/consent?token=${encodeURIComponent(consentRoute.token)}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({explicitlyAgree:explicitConsent})});
      const d=await r.json();if(!r.ok)throw Error(d.detail||d.message||'Could not record consent');
      setConsentView(d);
    }catch(e){setConsentError(e.message);}finally{setConsentBusy(false);}
  };
  const [conversation, setConversation] = useState(EXAMPLE);
  const [analysis, setAnalysis] = useState(null);
  const [agreement, setAgreement] = useState(null);
  const [draft, setDraft] = useState(null);
  const [paymentAmount, setPaymentAmount] = useState('1150.00');
  const [paypalOrder, setPaypalOrder] = useState(null);
  const [contractReceipt, setContractReceipt] = useState(null);
  const [finalReceipt, setFinalReceipt] = useState(null);
  const [locked, setLocked] = useState(null);
  const [pdfFile, setPdfFile] = useState(null);
  const [pdfVerification, setPdfVerification] = useState(null);
  const [answers, setAnswers] = useState({});
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [step, setStep] = useState(0);
  const [showConversation, setShowConversation] = useState(false);
  const terms = analysis?.terms || [];
  const open = useMemo(() => terms.filter(t => t.status !== 'CONFIRMED_IN_CHAT'), [terms]);
  const confirmed = terms.length - open.length;
  const complete = terms.length === REQUIRED.length && open.length === 0 && analysis?.completeness?.complete === true;
  const progress = terms.length ? Math.round(confirmed / REQUIRED.length * 100) : 0;

  function clearResults() { setAnalysis(null); setAgreement(null); setDraft(null); setPaypalOrder(null); setContractReceipt(null); setFinalReceipt(null); setLocked(null); setPdfFile(null); setPdfVerification(null); setAnswers({}); setStep(0); setError(''); }
  async function run(label, fn) { setBusy(label); setError(''); try { await fn(); } catch (e) { setError(e.message); } finally { setBusy(''); } }
  const analyze = () => run('analyze', async () => {
    const data = await api('/api/ai/analyze', { conversation });
    setAnalysis(data); setAgreement(null); setDraft(null); setPaypalOrder(null); setContractReceipt(null); setFinalReceipt(null); setPdfFile(null); setPdfVerification(null); setAnswers({}); setStep(1);
  });
  const continueNegotiation = () => run('continue', async () => {
    const payload = open.flatMap(term => ['CLIENT', 'DEVELOPER'].map(party => ({
      party, field: term.field, response: (answers[`${term.field}:${party}`] || '').trim()
    })));
    if (payload.some(a => !a.response)) throw new Error('Both parties must provide an answer for each unresolved term.');
    const continued = await api('/api/agreements/negotiation/continue', { conversation, answers: payload });
    setConversation(continued.updatedConversation);
    const next = await api('/api/ai/analyze', { conversation: continued.updatedConversation });
    setAnalysis(next); setAnswers({}); setStep(1);
  });
  const createAgreement = () => run('create', async () => {
    if (!complete) throw new Error('Resolve every required term before creating an agreement.');
    const data = await api('/api/agreements/from-analysis', { terms });
    setAgreement(data.agreement); setDraft(data.draft); setContractReceipt(null); setPdfFile(null); setPdfVerification(null); setStep(3);
  });
  const approve = party => run(party, async () => {
    const data = await api(`/api/agreements/${agreement.id}/approvals`, { party, version: agreement.version });
    setAgreement(data);
  });
  const lockAgreement = () => run('lock', async () => {
    const result = await api('/api/locked-consent/lock',{agreementId:agreement.id,version:agreement.version});
    setLocked(result);
  });
  const refreshLocked = () => run('refresh-lock', async () => {
    const response = await fetch(`/api/locked-consent/${encodeURIComponent(agreement.id)}`);
    const result = await response.json();
    if(!response.ok) throw new Error(result.detail||result.message||'Could not load consent status');
    setLocked(old=>({...old,...result,clientLink:old?.clientLink,developerLink:old?.developerLink}));
  });
  const createPayPalOrder = () => run('paypal-create', async () => {
    if (!locked?.clientApproved || !locked?.developerApproved) throw new Error('Both independent link approvals must be recorded first.');
    const data = await api('/api/paypal/sandbox/orders', { agreementId: agreement.id, version: agreement.version, amount: paymentAmount });
    setPaypalOrder(data);
    window.open(data.approvalUrl, '_blank', 'noopener,noreferrer');
  });
  const capturePayPalOrder = () => run('paypal-capture', async () => {
    if (!paypalOrder) throw new Error('Create an order first.');
    const data = await api(`/api/paypal/sandbox/orders/${encodeURIComponent(paypalOrder.orderId)}/capture`, {});
    setPaypalOrder(data);
  });

  const issueFinalPdf = () => run('final-issue', async () => {
    if (!locked?.clientApproved || !locked?.developerApproved) throw new Error('Both independent consents are required.');
    const receipt = await api('/api/finalized-contracts/issue',{agreementId:agreement.id,version:agreement.version});
    setFinalReceipt(receipt);setPdfFile(null);setPdfVerification(null);
  });
  const verifyFinalPdf = () => run('final-verify', async () => {
    if (!finalReceipt || !pdfFile) throw new Error('Download the finalized PDF and select a file.');
    const form = new FormData();form.append('file',pdfFile);
    const response = await fetch(`/api/finalized-contracts/${encodeURIComponent(finalReceipt.contractId)}/verify`,{method:'POST',body:form});
    const data = await response.json().catch(()=>({}));
    if(!response.ok) throw new Error(data.detail||data.message||'Verification failed');
    setPdfVerification(data);
  });
  const issueContractPdf = () => run('pdf-issue', async () => {
    if (!agreement?.bothApproved) throw new Error('Record both demo approvals before generating the PDF.');
    if (contractReceipt) throw new Error('A PDF has already been issued for this session. Download and verify that PDF instead.');
    const terms = (analysis?.terms || []).filter(t => t.status !== 'MISSING').map(t => ({field: t.field, value: t.value}));
    if (!terms.length) throw new Error('No terms are available to generate a draft.');
    const receipt = await api('/api/contracts/issue', { buyer: 'Client', seller: 'Developer', terms });
    setContractReceipt(receipt); setPdfVerification(null); setPdfFile(null);
  });
  const verifyContractPdf = () => run('pdf-verify', async () => {
    if (!contractReceipt?.contractId || !pdfFile) throw new Error('Generate a contract and choose a PDF file.');
    const form = new FormData();
    form.append('file', pdfFile);
    let response;
    try {
      response = await fetch(`/api/contracts/${encodeURIComponent(contractReceipt.contractId)}/verify`, {method: 'POST', body: form});
    } catch {
      throw new Error('Cannot reach the backend. Start Spring Boot on port 8080.');
    }
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.detail || data.message || `Verification failed (HTTP ${response.status})`);
    setPdfVerification(data);
  });
  const reset = () => { clearResults(); setConversation(EXAMPLE); setShowConversation(false); };

  if (paypalReturn) return <div className="app"><header className="topbar"><div className="shell header-inner"><Logo/></div></header><main className="shell" style={{maxWidth:760,paddingTop:40}}><section className="panel"><div className="panel-header"><div className="panel-icon green"><ShieldCheck size={20}/></div><div><h2>PayPal Sandbox checkout</h2><p>Back at DealSeal AI</p></div></div>{paypalReturn.status === 'cancelled' ? <div className="notice">Checkout was cancelled. No payment was captured by DealSeal.</div> : <>{returnBusy && <p>Verifying PayPal approval and capturing the Sandbox payment…</p>}{returnError && <div className="notice" role="alert">{returnError}</div>}{returnPayment?.status === 'COMPLETED' && <p className="payment-success"><CheckCircle2 size={17}/> Sandbox payment completed — {returnPayment.currency} {returnPayment.amount}. Order: {returnPayment.orderId}</p>}{returnPayment && returnPayment.status !== 'COMPLETED' && <div className="notice">PayPal reported order status: {returnPayment.status}. Payment is not confirmed complete.</div>}{returnError && paypalReturn.orderId && <button className="secondary" disabled={returnBusy} onClick={completeReturnedPayment}>Retry payment verification</button>}</>}<div className="panel-actions"><a className="secondary" href="/">Return to DealSeal demo</a></div></section></main></div>;
  if (consentRoute) return <div className="app"><header className="topbar"><div className="shell header-inner"><Logo/></div></header><main className="shell" style={{maxWidth:760,paddingTop:40}}><section className="panel"><div className="panel-header"><div className="panel-icon green"><ShieldCheck size={20}/></div><div><h2>Independent contract review</h2><p>Demo approval link — possession of this link does not verify identity.</p></div></div>{consentError && <div className="notice" role="alert">{consentError}</div>}{!consentView && !consentError && <p>Loading locked contract…</p>}{consentView && <><p><strong>Reviewing as:</strong> {consentView.party}</p><p><strong>Version:</strong> {consentView.version}</p><p style={{overflowWrap:'anywhere'}}><strong>Locked SHA-256:</strong> {consentView.sha256}</p><h3 className="section-title">Locked terms</h3><div className="clauses">{consentView.terms.map((t,i)=><div className="clause" key={i}><span>{String(i+1).padStart(2,'0')}</span><p>{t}</p></div>)}</div>{consentView.approved ? <p className="payment-success"><CheckCircle2 size={17}/> Your demo consent was recorded for this version.</p> : <><label style={{display:'flex',gap:10,alignItems:'flex-start',margin:'20px 0'}}><input type="checkbox" checked={explicitConsent} onChange={e=>setExplicitConsent(e.target.checked)}/> I have reviewed the terms above and explicitly approve this exact locked version (demo only).</label><button className="primary" disabled={!explicitConsent||consentBusy} onClick={submitIndependentConsent}>{consentBusy?'Recording…':'Record my consent'}</button></>}</>}</section></main></div>;
  return <div className="app">
    <header className="topbar"><div className="shell header-inner"><Logo/><div className="header-right"><span className="demo-pill"><span className="online-dot"/> HACKATHON DEMO</span><span className="header-version">v1.0</span></div></div></header>
    <main className="shell">
      <section className="hero"><div className="eyebrow"><Sparkles size={14}/> AI-POWERED AGREEMENT WORKSPACE</div><h1>From conversation <span>to clarity.</span></h1><p>Turn messy negotiations into structured terms, close the gaps, and review one shared agreement — before anyone says yes.</p></section>
      <nav className="steps" aria-label="Workflow progress">{STEPS.map((name,i) => <React.Fragment key={name}><button className={`step ${step === i ? 'active' : ''} ${i < step ? 'done' : ''}`} disabled={i > step || (i > 0 && !analysis)} onClick={() => setStep(i)}><span className="step-number">{i < step ? <Check size={14}/> : String(i + 1).padStart(2,'0')}</span><span>{name}</span></button>{i < STEPS.length-1 && <ChevronRight size={15} className="step-arrow"/>}</React.Fragment>)}</nav>
      {error && <div className="alert" role="alert"><CircleAlert size={19}/><span>{error}</span><button aria-label="Dismiss error" onClick={() => setError('')}><X size={16}/></button></div>}
      <div className="workspace">
        <div className="main-column">
          {step === 0 && <section className="panel"><div className="panel-header"><div className="panel-icon violet"><MessageSquareText size={20}/></div><div><h2>Start with a conversation</h2><p>Paste messages between the client and developer.</p></div></div><label htmlFor="conversation" className="field-label">CONVERSATION TRANSCRIPT</label><textarea id="conversation" className="conversation-input" value={conversation} maxLength={15000} onChange={e => {setConversation(e.target.value); if (analysis) clearResults();}} placeholder="Client: ...&#10;Developer: ..."/><div className="editor-footer"><span>{conversation.length.toLocaleString()} / 15,000 characters</span><button className="text-button" onClick={reset}>Restore example</button></div><button className="primary full" disabled={!!busy || !conversation.trim()} onClick={analyze}><Sparkles size={17}/>{busy === 'analyze' ? 'Analyzing conversation…' : 'Analyze agreement'}<ArrowRight size={17}/></button><div className="privacy-note"><LockKeyhole size={15}/> Conversation is sent to the configured backend AI provider for analysis. Avoid sensitive information.</div></section>}
          {step === 1 && <section className="panel"><div className="panel-header with-action"><div className="panel-heading"><div className="panel-icon violet"><ClipboardList size={20}/></div><div><h2>Agreement terms</h2><p>Seven essentials, extracted from your conversation.</p></div></div><button className="subtle-button" disabled={!!busy} onClick={() => setStep(0)}><FileText size={15}/> Edit transcript</button></div><div className="term-list">{terms.map((term,i) => <div className="term" key={term.field}><div className="term-top"><div className="term-index">{String(i+1).padStart(2,'0')}</div><h3>{term.field}</h3><span className={`status ${term.status === 'CONFIRMED_IN_CHAT' ? 'confirmed' : term.status === 'MISSING' ? 'missing' : 'proposed'}`}>{term.status === 'CONFIRMED_IN_CHAT' ? <CheckCircle2 size={13}/> : <CircleAlert size={13}/>} {STATUS[term.status] || term.status}</span></div><p className={`term-value ${term.status === 'MISSING' ? 'muted' : ''}`}>{term.status === 'MISSING' ? 'Not yet agreed' : term.value}</p>{term.status !== 'CONFIRMED_IN_CHAT' && term.question && <div className="term-question">{term.question}</div>}</div>)}</div><div className="panel-actions"><button className="secondary" onClick={() => setStep(0)}>Back</button>{open.length > 0 ? <button className="primary" onClick={() => setStep(2)}>Resolve {open.length} open {open.length === 1 ? 'term' : 'terms'} <ArrowRight size={17}/></button> : <button className="primary" disabled={!!busy || !complete} onClick={createAgreement}>{busy === 'create' ? 'Creating…' : 'Create review draft'} <ArrowRight size={17}/></button>}</div></section>}
          {step === 2 && <section className="panel"><div className="panel-header"><div className="panel-icon amber"><MessageSquareText size={20}/></div><div><h2>Resolve open terms</h2><p>Record each party's response, then let AI recheck the conversation.</p></div></div><div className="notice"><CircleAlert size={17}/> These are self-entered demo statements, not verified messages or signatures. Both parties must independently review the final agreement.</div>{open.length === 0 ? <p>All terms are confirmed in the analysis.</p> : <div className="question-list">{open.map((term,i) => <div className="question-card" key={term.field}><div className="question-heading"><span className="question-number">{String(i+1).padStart(2,'0')}</span><div><h3>{term.field}</h3><p>{term.question || `What should be agreed for ${term.field}?`}</p></div></div><div className="answer-grid">{['CLIENT','DEVELOPER'].map(party => <label key={party} className="answer-field"><span>{party === 'CLIENT' ? 'Client response' : 'Developer response'}</span><textarea maxLength={1000} rows={3} placeholder={party === 'CLIENT' ? 'Client states their terms…' : 'Developer states their terms…'} value={answers[`${term.field}:${party}`] || ''} onChange={e => setAnswers(a => ({...a,[`${term.field}:${party}`]:e.target.value}))}/></label>)}</div></div>)}</div>}<div className="panel-actions"><button className="secondary" onClick={() => setStep(1)}>Back to terms</button><button className="primary" disabled={!!busy || !open.length} onClick={continueNegotiation}>{busy === 'continue' ? 'Reanalyzing…' : 'Add responses & reanalyze'} <RefreshCw size={16}/></button></div></section>}
          {step === 3 && agreement && <section className="panel"><div className="panel-header"><div className="panel-icon green"><ShieldCheck size={20}/></div><div><h2>Review & approve</h2><p>Version {agreement.version} · Demo agreement</p></div></div><div className="agreement-banner"><div><span className="small-label">AGREEMENT REFERENCE</span><strong>{agreement.id}</strong></div><span className={`status ${agreement.bothApproved ? 'confirmed' : 'proposed'}`}>{agreement.bothApproved ? 'Both demo approvals recorded' : 'Awaiting approvals'}</span></div><h3 className="section-title">Draft clauses</h3><div className="clauses">{agreement.terms.map((clause,i)=><div key={i} className="clause"><span>{String(i+1).padStart(2,'0')}</span><p>{clause}</p></div>)}</div><h3 className="section-title">Party approvals</h3><div className="approval-grid">{[['CLIENT',agreement.clientApproved],['DEVELOPER',agreement.developerApproved]].map(([party,approved])=><div className="approval-card" key={party}><span className="small-label">{party}</span><strong>{approved ? 'Approval recorded' : 'Not approved yet'}</strong><button className={approved ? 'approved-button' : 'secondary'} disabled={!!busy || approved} onClick={() => approve(party)}>{approved ? <><Check size={16}/> Recorded</> : busy === party ? 'Recording…' : `Approve as ${party.toLowerCase()}`}</button></div>)}</div><div className="paypal-checkout"><h3><LockKeyhole size={18} style={{verticalAlign:'middle',marginRight:8}}/>Locked contract & independent consent</h3><p>Lock this agreement version and share separate review links. Each party must open their own link, review the exact terms and explicitly consent. Demo only: links are bearer credentials, not identity verification or legal signatures.</p>{!locked ? <button className="primary" disabled={!!busy} onClick={lockAgreement}>{busy==='lock'?'Locking…':'Lock this contract version'}</button> : <><p style={{overflowWrap:'anywhere'}}><strong>Locked SHA-256:</strong> {locked.sha256}</p><p>Client: <strong>{locked.clientApproved?'Approved':'Awaiting consent'}</strong> · Developer: <strong>{locked.developerApproved?'Approved':'Awaiting consent'}</strong></p>{[['Client',locked.clientLink],['Developer',locked.developerLink]].map(([name,link])=>link&&<p key={name}><a href={link} target="_blank" rel="noopener noreferrer">Open {name.toLowerCase()} approval link</a> · <button className="text-button" onClick={()=>navigator.clipboard.writeText(window.location.origin+link)}>Copy private link</button></p>)}<button className="secondary" disabled={!!busy} onClick={refreshLocked}>Refresh consent status</button>{locked.clientApproved&&locked.developerApproved&&<p className="payment-success"><CheckCircle2 size={16}/> Both parties consented to this locked version (demo).</p>}</>}</div>{locked?.clientApproved && locked?.developerApproved && <div className="paypal-checkout"><h3><FileText size={18} style={{verticalAlign:'middle',marginRight:8}}/>Unified agreement & consent PDF</h3><p>One document with locked terms, both parties' demo consent timestamps, and agreement fingerprint. The document is not a digitally signed PDF or identity verification.</p><button className="primary" disabled={!!busy||!!finalReceipt} onClick={issueFinalPdf}>{busy==='final-issue'?'Generating…':finalReceipt?'Final PDF issued':'Generate unified consent PDF'}</button>{finalReceipt && <div className="paypal-order"><p><strong>Document:</strong> {finalReceipt.contractId}</p><p><strong>Status:</strong> {finalReceipt.status}</p><p style={{overflowWrap:'anywhere'}}><strong>PDF SHA-256:</strong> {finalReceipt.pdfSha256}</p><a href={finalReceipt.downloadUrl} download>Download unified PDF</a><div style={{marginTop:16}}><label className="field-label" htmlFor="finalPdfFile">VERIFY UNIFIED PDF</label><input id="finalPdfFile" type="file" accept="application/pdf,.pdf" onChange={e=>{setPdfFile(e.target.files?.[0]||null);setPdfVerification(null);}}/><button className="secondary" disabled={!!busy||!pdfFile} onClick={verifyFinalPdf}>{busy==='final-verify'?'Verifying…':'Verify unified PDF'}</button>{pdfVerification && <p role="status" className={pdfVerification.matchesOriginal?'payment-success':'notice'}>{pdfVerification.matchesOriginal?'MATCH — File matches the issued PDF.':'MISMATCH — File differs from the issued PDF.'}</p>}</div></div>}</div>}{locked?.clientApproved && locked?.developerApproved && <div className="paypal-checkout"><h3>PayPal Sandbox checkout</h3><p>Test payments only. Enter an explicit USD amount, then approve the order using a PayPal Sandbox buyer account. Creating an order does not charge anyone.</p><label className="field-label" htmlFor="paymentAmount">PAYMENT AMOUNT (USD)</label><input id="paymentAmount" type="number" min="0.01" max="10000" step="0.01" value={paymentAmount} onChange={e => setPaymentAmount(e.target.value)} disabled={!!paypalOrder}/>{!paypalOrder ? <button className="primary" disabled={!!busy} onClick={createPayPalOrder}>{busy === 'paypal-create' ? 'Creating order…' : 'Create PayPal Sandbox order'} <ArrowRight size={16}/></button> : <div className="paypal-order"><p>Order: <strong>{paypalOrder.orderId}</strong> · {paypalOrder.status}</p>{paypalOrder.approvalUrl && <a href={paypalOrder.approvalUrl} target="_blank" rel="noopener noreferrer">Open Sandbox approval page</a>}{paypalOrder.status !== 'COMPLETED' && <button className="primary" disabled={!!busy} onClick={capturePayPalOrder}>{busy === 'paypal-capture' ? 'Checking payment…' : 'Capture approved Sandbox payment'}</button>}{paypalOrder.status === 'COMPLETED' && <p className="payment-success"><CheckCircle2 size={16}/> Sandbox payment completed</p>}</div>}</div>}<div className="notice legal"><LockKeyhole size={17}/> Demo only: roles are self-asserted, approvals are not legally verified signatures, and no payment is initiated.</div><div className="panel-actions"><button className="secondary" onClick={reset}>Start new agreement</button></div></section>}
        </div>
        <aside className="side-column">
          <div className="sidebar-card summary"><div className="sidebar-top"><span>AGREEMENT READINESS</span><ShieldCheck size={17}/></div><div className="readiness-number">{terms.length ? progress : 0}<span>%</span></div><div className="progress-track"><div style={{width:`${progress}%`}}/></div><div className="summary-row"><span><i className="dot green-dot"/> Confirmed terms</span><strong>{confirmed}</strong></div><div className="summary-row"><span><i className="dot amber-dot"/> Need resolution</span><strong>{open.length}</strong></div><div className="summary-row"><span><i className="dot gray-dot"/> Required categories</span><strong>{REQUIRED.length}</strong></div><div className="summary-message">{!analysis ? 'Analyze a conversation to see agreement readiness.' : complete ? 'All required terms confirmed in chat. Ready for human review.' : 'Resolve outstanding terms before generating an approvable agreement.'}</div></div>
          <div className="sidebar-card process"><h3>How DealSeal works</h3><div className="process-item"><span className="process-icon"><Sparkles size={16}/></span><div><strong>AI extracts terms</strong><p>Identify promises, proposals, and missing details.</p></div></div><div className="process-item"><span className="process-icon"><MessageSquareText size={16}/></span><div><strong>Close the gaps</strong><p>Gather both perspectives and reanalyze.</p></div></div><div className="process-item"><span className="process-icon"><ShieldCheck size={16}/></span><div><strong>Review together</strong><p>Approve the same version of the draft.</p></div></div></div>
          <div className="paypal-card"><div className="paypal-icon"><Wallet size={20}/></div><div><strong>Payments come later</strong><p>PayPal integration is planned. This demo does not collect or move money.</p></div></div>
          {analysis && <button className="transcript-toggle" onClick={() => setShowConversation(!showConversation)}><FileText size={16}/>{showConversation ? 'Hide' : 'View'} current transcript <ChevronRight size={15}/></button>}
          {showConversation && <pre className="transcript-preview">{conversation}</pre>}
        </aside>
      </div>
    </main>
    <footer><div className="shell footer-inner"><Logo/><span>Built for clarity, not legal advice. · Prototype only.</span></div></footer>
  </div>;
}
