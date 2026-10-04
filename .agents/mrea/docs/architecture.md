# MREA Architecture

## Overview

MREA is a governance framework for AI-assisted software development.

It provides a **control plane** that orchestrates specialized agents with clear authority boundaries, quality gates, and human approval.

MREA does **not** replace agent runtimes (e.g., OpenCode, Cursor). It layers governance on top of them.

```
┌─────────────────────────────────────────────────────────────┐
│                     USER INTERFACE                         │
│                  (Human + Agent Runtime)                   │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    ORCHESTRATOR                            │
│                 (Control Plane)                            │
│                                                           │
│  - Intent classification                                   │
│  - Risk classification                                     │
│  - Workflow selection                                      │
│  - Delegation to specialists                               │
│  - Quality gate enforcement                                │
│  - Human approval routing                                  │
└─────────────────────────┬───────────────────────────────────┘
                          │
           ┌──────────────┼──────────────┐
           │              │              │
           ▼              ▼              ▼
┌─────────────────┐ ┌─────────────┐ ┌─────────────────┐
│   ARCHITECTS    │ │  AUDITORS   │ │   IMPLEMENTER   │
│                 │ │             │ │                 │
│ - Software      │ │ - Technical │ │ - Execute       │
│ - Advanced      │ │ - Design    │ │   approved      │
│ - Design        │ │             │ │   plans         │
└─────────────────┘ └─────────────┘ └─────────────────┘
```

---

## Core Components

### 1. Orchestrator

**Single entry point** for all requests.

Responsible for:
- Intent classification
- Risk classification (see `risk-model.md`)
- Workflow selection
- Delegation to specialists
- Quality gate enforcement
- Human approval routing

**Boundaries:**
- Does **not** implement changes
- Does **not** modify files
- Does **not** execute commands

### 2. Architects

Design solutions and produce implementation plans.

| Role | When to use |
|------|-------------|
| **Software Architect** | Standard, localized changes |
| **Advanced Software Architect** | Cross-cutting, high-impact, or critical changes |
| **Design Architect** | UX, UI, and Frontend architecture |

**Boundaries:**
- Do **not** implement
- Do **not** audit
- Do **not** modify files

### 3. Auditors

Independently validate plans and implementations.

| Role | Focus |
|------|-------|
| **Technical Auditor** | Software architecture, security, complexity |
| **Design Auditor** | UX, UI, Design System compliance |

**Boundaries:**
- Do **not** implement
- Do **not** propose solutions
- Do **not** modify files

### 4. Implementer

Executes approved plans.

Responsible for:
- Reading the plan and affected files
- Modifying only necessary files
- Running validations (build, typecheck, tests)
- Delivering a summary of changes

**Boundaries:**
- Does **not** redesign solutions
- Does **not** change architecture
- Does **not** add unsolicited features

---

## Governance Model

Separation of concerns ensures no single agent designs, validates, and implements a solution.

| Role | Designs | Validates | Implements |
|------|---------|-----------|------------|
| Orchestrator | ❌ | ❌ | ❌ |
| Architect | ✅ | ❌ | ❌ |
| Auditor | ❌ | ✅ | ❌ |
| Implementer | ❌ | ❌ | ✅ |

---

## Quality Gates

Each stage ends with a quality gate.

| Gate | Owner | Purpose |
|------|-------|---------|
| Discovery | Orchestrator | Evidence gathered |
| Architecture | Architect | Plan approved |
| Audit | Auditor | Plan validated |
| Human Approval | User | Explicit permission |
| Implementation | Implementer | Verified changes |

Gate outcomes:
- **PASS** — Approved; continue.
- **REWORK** — Issues found; correct and retry (max 3 iterations).
- **BLOCK** — Cannot proceed safely; escalate to human.

---

## Workflow Types

The Orchestrator selects workflows based on risk and domain.

| Workflow | Description |
|----------|-------------|
| **Fast Path** | Low risk, non-material changes. Skips architecture and audit. |
| **Standard Architecture** | Medium risk. Architect → Implementation. |
| **Advanced Architecture** | High risk. Advanced Architect → Implementation. |
| **Audited Implementation** | High/Critical risk. Architect → Mandatory Audit → Implementation. |
| **Complete Delivery** | Cross-domain. Parallel architecture, consolidation, audit, implementation. |

See `workflow.md` for detailed diagrams.

---

## Skills Integration

Skills provide operational knowledge to agents.

```
skills/
├── engineering-protocol.md
├── ponytail-philosophy.md
├── happy-path.md
├── evidence-based-validation.md
├── security-baseline.md
└── rollback-strategy.md
```

Agents load skills applicable to their domain (e.g., `engineering-protocol` for Architects and Implementer).

---

## Core vs Platform Adapters

MREA is **tool-agnostic**.

- **`core/`** — Normative prompts. Platform-independent.
- **`platforms/`** — Platform-specific adapters (e.g., OpenCode) with frontmatter and permissions.

This ensures governance survives changes in models and platforms.

---

## Version

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-08-24 | Initial release |