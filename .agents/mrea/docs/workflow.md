# MREA Workflows

## Overview

MREA defines a set of workflows that the Orchestrator selects based on risk classification and task domain.

Each workflow is a sequence of stages, with **quality gates** between stages.

---

## Workflow Catalog

### W0 — Direct Response

**Use when:** The user asks a question that does not require project analysis or delegation.

```
USER
↓
ORCHESTRATOR
↓
RESPONSE
```

**Gates:** None.

---

### W1 — Repository Discovery

**Use when:** The user wants to understand where something lives in the codebase.

```
ORCHESTRATOR
↓
EXPLORE (subagent)
↓
VALIDATE EVIDENCE
↓
RESPONSE
```

**Gates:**
- Discovery Gate: Evidence gathered.

---

### W2 — External Research

**Use when:** The user needs information about external dependencies, APIs, or frameworks.

```
ORCHESTRATOR
↓
WEBSEARCH / WEBFETCH
↓
GENERAL (subagent)
↓
VALIDATE SOURCES
↓
RESPONSE
```

**Gates:**
- Source Validation: Sources are authoritative and relevant.

---

### W3 — Delegated General Analysis

**Use when:** The task requires synthesis but does not fit a specialist domain.

```
ORCHESTRATOR
↓
GENERAL (subagent)
↓
VALIDATE OUTPUT
↓
RESPONSE
```

**Gates:**
- Output Gate: Analysis is complete and verifiable.

---

### W4 — Standard Architecture

**Use when:** The task requires standard software architecture (low/medium risk).

```
DISCOVERY
↓
enterprise-architect
↓
ARCHITECTURE GATE
↓
RESPONSE
```

**Gates:**
- Architecture Gate: Plan is complete, evidence-based, and minimal.

---

### W5 — Advanced Architecture

**Use when:** The task requires advanced architecture (high/critical risk).

```
L3/L4 DISCOVERY
↓
enterprise-advanced-architect
↓
ADVANCED ARCHITECTURE GATE
↓
RESPONSE
```

**Gates:**
- Advanced Architecture Gate: Plan includes alternatives, trade-offs, risks, and reversibility.

---

### W6 — Design Architecture

**Use when:** The task requires UX/UI or Frontend architecture.

```
DISCOVERY
↓
enterprise-design-architect
↓
DESIGN GATE
↓
RESPONSE
```

**Gates:**
- Design Gate: UX analysis, user flows, accessibility, and Design System compliance are documented.

---

### W7 — Software Audit

**Use when:** The user requests a technical audit of existing code or a plan.

```
DISCOVERY
↓
enterprise-auditor
↓
AUDIT GATE
↓
RESPONSE
```

**Gates:**
- Audit Gate: Findings are documented with evidence and severity.

---

### W8 — Design Audit

**Use when:** The user requests a UX/UI audit.

```
DISCOVERY
↓
enterprise-design-auditor
↓
DESIGN GATE
↓
RESPONSE
```

**Gates:**
- Design Gate: UX/UI findings are documented with evidence.

---

### W9 — Standard Architecture → Implementation

**Use when:** A standard architecture plan needs to be implemented.

```
DISCOVERY
↓
enterprise-architect
↓
ARCHITECTURE GATE
↓
CONCEPTUAL SUMMARY AND SCOPE
↓
REQUEST HUMAN CONFIRMATION
↓
WAIT FOR CONFIRMATION
↓
enterprise-implementer
↓
IMPLEMENTATION GATE
```

**Gates:**
- Architecture Gate
- Human Approval
- Implementation Gate

---

### W10 — Advanced Architecture → Audit → Implementation → Final Audit

**Use when:** A critical change requires advanced architecture, mandatory audit, and post-implementation validation.

```
L3/L4 DISCOVERY
↓
enterprise-advanced-architect
↓
ADVANCED ARCHITECTURE GATE
↓
enterprise-auditor
↓
AUDIT GATE
↓
CONCEPTUAL SUMMARY AND SCOPE
↓
REQUEST HUMAN CONFIRMATION
↓
WAIT FOR CONFIRMATION
↓
enterprise-implementer
↓
VALIDATION / FINAL AUDIT
```

**Gates:**
- Advanced Architecture Gate
- Audit Gate
- Human Approval
- Implementation Gate
- Final Audit

---

### W11 — Design → Implementation → Design Audit

**Use when:** A design plan needs to be implemented and audited.

```
DISCOVERY
↓
enterprise-design-architect
↓
DESIGN GATE
↓
CONCEPTUAL SUMMARY AND SCOPE
↓
REQUEST HUMAN CONFIRMATION
↓
WAIT FOR CONFIRMATION
↓
enterprise-implementer
↓
enterprise-design-auditor
↓
DESIGN GATE
```

**Gates:**
- Design Gate
- Human Approval
- Implementation Gate
- Design Audit Gate

---

### W12 — Complete Product Delivery

**Use when:** Software and design are materially coupled and must be delivered together.

```
                         ┌── SOFTWARE ARCHITECT (STD OR ADV) ──┐
                         │                                    │
DISCOVERY ──────────────┼── DESIGN ARCHITECT ────────────────┼──→ CONSOLIDATION
                         │                                    │
                         └────────────────────────────────────┘
                                           ↓
                                   ARCHITECTURE GATE
                                           ↓
                                      DESIGN GATE
                                           ↓
                                 CONCEPTUAL SUMMARY AND SCOPE
                                           ↓
                                REQUEST HUMAN CONFIRMATION
                                           ↓
                                   WAIT FOR CONFIRMATION
                                           ↓
                                 enterprise-implementer
                                           ↓
                         ┌─────────────────┴─────────────────┐
                         ↓                                   ↓
                enterprise-auditor               enterprise-design-auditor
                         └─────────────────┬─────────────────┘
                                           ↓
                                     RELEASE GATE
```

**Gates:**
- Architecture Gate
- Design Gate
- Human Approval
- Implementation Gate
- Software Audit Gate
- Design Audit Gate
- Release Gate

---

## Workflow Selection Rules

The Orchestrator selects the workflow based on:

1. **Risk classification** (Low/Medium/High/Critical)
2. **Materiality** (Is the change material?)
3. **Domain** (Software, Design, or both)
4. **User intent** (Discovery, Architecture, Audit, Implementation)

| Risk Level | Material? | Domain | Workflow |
|------------|-----------|--------|----------|
| Low | No | Any | W0, W1, W2, W3 |
| Low/Medium | Yes | Software | W4 → W9 |
| High | Yes | Software | W5 → W10 |
| Medium/High | Yes | Design | W6 → W11 |
| High/Critical | Yes | Both | W12 |

---

## Version

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-08-24 | Initial release |