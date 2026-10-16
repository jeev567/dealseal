import React, { useMemo, useState } from 'react';
import { ArrowRight, Check, CheckCircle2, ChevronRight, CircleAlert, ClipboardList, FileText, LockKeyhole, MessageSquareText, RefreshCw, ShieldCheck, Sparkles, Wallet, X } from 'lucide-react';

const EXAMPLE = `Client: I need a website for $2,000, delivered November 15.
Developer: I can do $2,300, delivered November 20.
Client: $2,300 works. Two revisions included?
Developer: Yes, two revisions included.
Client: I agree to the November 20 delivery date.
Developer: Confirmed. I will deliver the website on November 20.
Client: I will pay $1,150 upfront and $1,150 after delivery.
Developer: Agreed to the payment schedule.`;
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
  const [conversation, setConversation] = useState(EXAMPLE);
  const [analysis, setAnalysis] = useState(null);
  const [agreement, setAgreement] = useState(null);
  const [draft, setDraft] = useState(null);
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

  function clearResults() { setAnalysis(null); setAgreement(null); setDraft(null); setAnswers({}); setStep(0); setError(''); }
  async function run(label, fn) { setBusy(label); setError(''); try { await fn(); } catch (e) { setError(e.message); } finally { setBusy(''); } }
  const analyze = () => run('analyze', async () => {
    const data = await api('/api/ai/analyze', { conversation });
    setAnalysis(data); setAgreement(null); setDraft(null); setAnswers({}); setStep(1);
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
    setAgreement(data.agreement); setDraft(data.draft); setStep(3);
  });
  const approve = party => run(party, async () => {
    const data = await api(`/api/agreements/${agreement.id}/approvals`, { party, version: agreement.version });
    setAgreement(data);
  });
  const reset = () => { clearResults(); setConversation(EXAMPLE); setShowConversation(false); };

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
          {step === 3 && agreement && <section className="panel"><div className="panel-header"><div className="panel-icon green"><ShieldCheck size={20}/></div><div><h2>Review & approve</h2><p>Version {agreement.version} · Demo agreement</p></div></div><div className="agreement-banner"><div><span className="small-label">AGREEMENT REFERENCE</span><strong>{agreement.id}</strong></div><span className={`status ${agreement.bothApproved ? 'confirmed' : 'proposed'}`}>{agreement.bothApproved ? 'Both demo approvals recorded' : 'Awaiting approvals'}</span></div><h3 className="section-title">Draft clauses</h3><div className="clauses">{agreement.terms.map((clause,i)=><div key={i} className="clause"><span>{String(i+1).padStart(2,'0')}</span><p>{clause}</p></div>)}</div><h3 className="section-title">Party approvals</h3><div className="approval-grid">{[['CLIENT',agreement.clientApproved],['DEVELOPER',agreement.developerApproved]].map(([party,approved])=><div className="approval-card" key={party}><span className="small-label">{party}</span><strong>{approved ? 'Approval recorded' : 'Not approved yet'}</strong><button className={approved ? 'approved-button' : 'secondary'} disabled={!!busy || approved} onClick={() => approve(party)}>{approved ? <><Check size={16}/> Recorded</> : busy === party ? 'Recording…' : `Approve as ${party.toLowerCase()}`}</button></div>)}</div><div className="notice legal"><LockKeyhole size={17}/> Demo only: roles are self-asserted, approvals are not legally verified signatures, and no payment is initiated.</div><div className="panel-actions"><button className="secondary" onClick={reset}>Start new agreement</button></div></section>}
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
