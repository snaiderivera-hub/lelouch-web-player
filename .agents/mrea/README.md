# 👔 MREA — Multi-Role Enterprise Agents
<p align="center">
  <img src="https://img.shields.io/badge/Estado-Beta-blue?style=for-the-badge" alt="Beta">
  <img src="https://img.shields.io/badge/Contribuciones-Bienvenidas-brightgreen?style=for-the-badge" alt="Contributions Welcome">
  <img src="https://img.shields.io/badge/Licencia-MIT-yellow?style=for-the-badge" alt="MIT License">
</p>
> **A governance framework for AI-assisted enterprise software development.**
>
> Specialized agents. Clear authority boundaries. Evidence-based decisions. Human approval before implementation.

---

## The Problem

Most AI coding workflows still look like this:

```
User
  │
  ▼
One giant agent
  │
  ├── Understands the request
  ├── Explores the codebase
  ├── Designs the solution
  ├── Writes the code
  ├── Reviews its own work
  └── Explains what it did
```

This works for small tasks.

For complex or enterprise codebases, it creates predictable failure modes:

- **Overengineering** — unnecessary abstractions, wrappers, dependencies, and boilerplate.
- **Scope creep** — the agent "improves" things that were never requested.
- **Self-validation bias** — the same agent designs, implements, and approves its own decisions.
- **Context pollution** — architecture, implementation, debugging, and conversation compete for the same context.
- **Doom loops** — agents repeatedly attempt to fix problems they introduced themselves.
- **Token waste** — expensive reasoning models are used for tasks that don't require them.

MREA separates these responsibilities.

---

## 🧠 The MREA Architecture

```
                               ┌──────────────┐
                               │     USER     │
                               └──────┬───────┘
                                      │
                                      ▼
                        ┌─────────────────────────┐
                        │      ORCHESTRATOR       │
                        │     Control Plane       │
                        └────────────┬────────────┘
                                     │
                      ┌──────────────┴──────────────┐
                      │                             │
                      ▼                             ▼
            ┌──────────────────┐          ┌──────────────────┐
            │ SOFTWARE         │          │ DESIGN           │
            │ ARCHITECT        │          │ ARCHITECT        │
            └────────┬─────────┘          └────────┬─────────┘
                     │                             │
                     ▼                             ▼
            ┌──────────────────┐          ┌──────────────────┐
            │ TECHNICAL        │          │ DESIGN           │
            │ AUDITOR          │          │ AUDITOR          │
            └────────┬─────────┘          └────────┬─────────┘
                     │                             │
                     └──────────────┬──────────────┘
                                    │
                                    ▼
                           ┌────────────────┐
                           │ HUMAN APPROVAL │
                           └───────┬────────┘
                                   │
                                   ▼
                           ┌────────────────┐
                           │  IMPLEMENTER   │
                           └───────┬────────┘
                                   │
                                   ▼
                                RESULT
```

The core principle is simple:

> **The agent that designs the solution should not be the same agent that validates it. The agent that validates it should not be the agent that implements it.**

---

## ⚙️ How MREA Works

MREA treats AI-assisted development as a controlled pipeline instead of a conversation.

```
REQUEST
   │
   ▼
DISCOVERY
   │
   ▼
RISK CLASSIFICATION (see docs/risk-model.md)
   │
   ├── NON-MATERIAL CHANGE ──────────────────────┐
   │                                              │
   ▼                                              │
MATERIAL CHANGE                                   │
   │                                              │
   ▼                                              │
ARCHITECTURE                                      │
   │                                              │
   ▼                                              │
INDEPENDENT AUDIT (mandatory for material        │
   │  changes: high-impact, security-sensitive,   │
   │  cross-system, migrations, public contracts, │
   │  difficult/irreversible, critical)          │
   ├── Rejected ──────────┐                      │
   │                       │                      │
   ▼                       ▼                      │
APPROVED PLAN      (rework loop)                 │
   │                                              │
   ▼                                              │
HUMAN APPROVAL ◄──────────────────────────────────┘
   │
   ▼
IMPLEMENTATION
   │
   ▼
VALIDATION
```

> **Audit Rule:** Independent audit is **mandatory** for any material change. A change is considered material if it affects: critical behavior, security, cross-system integrations, data migrations, public contracts, or if rollback is difficult or impossible. For non-material changes (e.g., typo fixes, label changes), audit may be omitted at the Orchestrator's discretion, provided the change is demonstrably non-material.

Implementation does not start automatically.

The system must first reach:

1. **A clear understanding of the request**
2. **Evidence from the existing codebase**
3. **An explicit implementation plan**
4. **Independent audit** (when required)
5. **Explicit human approval**

Only then can code be modified.

---

## 👥 The Squad

MREA follows a strict hierarchy:

> **The Orchestrator is the primary agent** — the single entry point for all requests. It coordinates the workflow, delegates tasks, enforces quality gates, and manages human approval.
>
> **All other roles are specialized subagents** — they are invoked by the Orchestrator to perform specific, bounded tasks (architecture, audit, implementation). They do not act independently and have no authority to initiate or modify the workflow on their own.

This creates clear authority boundaries and prevents agents from stepping outside their responsibilities.

The roles are:

| Role | Responsibility | Cannot |
|------|----------------|--------|
| 🧠 **Orchestrator** _(Primary)_ | Routes work, enforces workflow and quality gates | Implement changes |
| 📐 **Software Architect** _(Subagent)_ | Designs standard backend and system changes | Implement changes |
| 🏛️ **Advanced Software Architect** _(Subagent)_ | Designs complex, critical, high-impact, or cross-system architecture | Implement changes |
| 🎨 **Design Architect** _(Subagent)_ | Designs UI, UX and frontend architecture | Implement changes |
| 🛡️ **Technical Auditor** _(Subagent)_ | Challenges architecture and detects unnecessary complexity | Implement changes |
| 👁️ **Design Auditor** _(Subagent)_ | Validates UX, UI and Design System compliance | Implement changes |
| 👷 **Implementer** _(Subagent)_ | Executes the approved plan | Redesign the solution |

This creates **intentional friction**.

Not every agent is allowed to solve every problem.

---

## 🐴 The Ponytail Philosophy

MREA is built around one uncomfortable assumption:

> **Code is a liability.**

Every new line of code creates something that must eventually be:

- maintained
- tested
- debugged
- secured
- documented
- understood

Therefore, before adding code, MREA asks:

```
Can this be solved with existing code?
        │
        ├── Yes → Reuse it.
        │
        ▼
Can the framework solve it natively?
        │
        ├── Yes → Use the native capability.
        │
        ▼
Can the requirement be simplified?
        │
        ├── Yes → Reduce the problem.
        │
        ▼
Only then → Write new code.
```

The Ponytail Philosophy is not "write less code for the sake of it."

It is:

> **Eliminate unnecessary complexity before creating new complexity.**

---

## 💰 FinOps by Design

Not every task deserves the same model.

MREA separates **reasoning cost** from **execution cost**.

```
High-cost reasoning models
        │
        ├── Architecture
        ├── Complex analysis
        └── Independent audits

Cost-efficient execution models
        │
        ├── Implementation
        ├── Mechanical changes
        └── Focused code generation
```

This allows model routing based on the actual complexity of the task instead of blindly assigning the most expensive model to everything.

### Recommended Model Routing (Production-Tested)

Based on our production experience, we recommend the following model assignments:

| Agent | Recommended Model | Reasoning Effort | Text Verbosity | Temperature |
|-------|-------------------|------------------|----------------|-------------|
| **Orchestrator** | `deepseek-v4-flash` | `max` | `low` | `0.5` |
| **Software Architect** | `hy3` | `high` | `low` | `0.0` |
| **Advanced Software Architect** | `glm-5.2` | `max` | `low` | `0.0` |
| **Design Architect** | `mimo-v2.5-pro` | _(default)_ | _(default)_ | `0.1` |
| **Software Auditor** | `deepseek-v4-pro` | `max` | `low` | `0.0` |
| **Design Auditor** | `deepseek-v4-pro` | `max` | `low` | `0.1` |
| **Implementer** | `deepseek-v4-flash` | `max` | `low` | `0.0` |

> **Note:** These recommendations are also documented as commented frontmatter in the OpenCode agent files (`platforms/opencode/agents/`). You can uncomment them to enforce the recommendations in your OpenCode environment.

These are **recommendations only**. You can substitute any model that fits your budget and quality requirements. The exact models are **not part of the framework** — MREA defines the roles, you decide which models fill them.

---

## 🔒 Bounded Autonomy

Agents should not have unlimited freedom.

MREA uses:

- Explicit role boundaries
- Permission restrictions
- Delegation contracts
- Execution budgets
- Iteration limits
- Quality gates
- Human approval before implementation

The goal is not maximum autonomy.

The goal is **predictable autonomy**.

---

## 🧩 Tool Agnostic by Design

The core framework contains no dependency on a specific AI coding platform.

```
core/
│
├── orchestrator.md
├── enterprise-architect.md
├── enterprise-advanced-architect.md
├── design-architect.md
├── technical-auditor.md
├── design-auditor.md
└── implementer.md
```

Platform-specific implementations are adapters:

```
platforms/
│
├── opencode/          # Fully configured for OpenCode
│
└── (community adapters can be added here)
```

> **Note:** MREA is **tool-agnostic** and can be adapted to any AI coding platform. This repository includes a **fully configured adapter for OpenCode** that we have tested in production. If you use a different platform, you can use the `core/` prompts as a starting point and build your own adapter. **Community contributions for new platform adapters are welcome!** See `CONTRIBUTING.md` for guidelines.

---

## 📂 Repository Structure

```
mrea-framework/
│
├── README.md
├── MANIFEST.md                     # Procesal Manifesto (English)
├── MANIFIESTO.md                   # Manifiesto Procesal (Spanish)
├── CONTRIBUTING.md                 # Contribution guidelines
├── LICENSE                         # MIT License
│
├── core/                           # Tool-agnostic agent definitions
│   ├── orchestrator.md
│   ├── enterprise-architect.md
│   ├── enterprise-advanced-architect.md
│   ├── design-architect.md
│   ├── technical-auditor.md
│   ├── design-auditor.md
│   └── implementer.md
│
├── platforms/                      # Platform-specific adapters
│   └── opencode/                   # Fully configured for OpenCode
│       ├── agents/
│       │   ├── enterprise-orchestrator.md
│       │   ├── enterprise-architect.md
│       │   ├── enterprise-advanced-architect.md
│       │   ├── enterprise-design-architect.md
│       │   ├── enterprise-auditor.md
│       │   ├── enterprise-design-auditor.md
│       │   └── enterprise-implementer.md
│       └── skills/
│           ├── engineering-protocol/
│           ├── evidence-based-validation/
│           ├── happy-path/
│           ├── ponytail-philosophy/
│           ├── rollback-strategy/
│           └── security-baseline/
│
├── skills/                         # Reusable skill catalog (tool-agnostic)
│   ├── README.md
│   ├── engineering-protocol.md
│   ├── ponytail-philosophy.md
│   ├── happy-path.md
│   ├── evidence-based-validation.md
│   ├── security-baseline.md
│   └── rollback-strategy.md
│
└── docs/                           # Extended documentation
    ├── architecture.md
    ├── workflow.md
    ├── ponytail-philosophy.md
    ├── model-routing.md
    ├── risk-model.md
    ├── audit-guide.md
    └── security-model.md
```

---

## 🚀 Quick Start

### 1. Clone

```
git clone https://github.com/JairValle/mrea-framework.git
```

### 2. Choose your runtime

Use the OpenCode adapter:

```
platforms/opencode/
```

Or start with the tool-agnostic core prompts:

```
core/
```

### 3. Start with the Orchestrator

Send your request to the Orchestrator.

MREA handles the routing:

```
Request
  ↓
Discovery
  ↓
Risk Classification
  ↓
Architecture (if material)
  ↓
Audit (if material)
  ↓
Human approval
  ↓
Implementation
```

> **Note:** The Orchestrator will automatically consult the skill catalog (`skills/README.md`) to determine which skills apply to your task. You can add your own skills by placing them in the `skills/` directory.

---

## 🎯 Who Is MREA For?

MREA is designed for teams and organizations that need governance, not just automation.

| Profile | Recommendation |
|---------|----------------|
| **Individual developer** | MREA may be overkill. Consider using just the Orchestrator + Implementer for simple workflows. |
| **Small team (3-5)** | ✅ MREA is ideal. Start with the core roles and adapt as needed. |
| **Enterprise team** | ✅ MREA is designed for you. Use the full role set and OpenCode adapter. |
| **Open source project** | ✅ MREA can help maintain quality. Start with the core prompts. |

If you're unsure, start with the core prompts and add complexity only when needed.

---

## 🎯 What MREA Is

- A multi-agent governance framework
- A role-based architecture for AI-assisted development
- Tool and model agnostic
- Designed for complex and enterprise codebases
- Focused on complexity control
- Optimized for predictable token usage
- Human-in-the-loop before implementation

## ❌ What MREA Is Not

- An autonomous software factory
- A replacement for engineering judgment
- A collection of "magic prompts"
- A guarantee that LLMs will not make mistakes
- A requirement to use a specific model or provider
- A runtime execution platform

---

## 🤝 Contributing

Contributions are welcome! Please read `CONTRIBUTING.md` for guidelines.

But before adding complexity, ask:

> **Can this feature be solved by removing something instead?**

If yes, that's probably the better contribution.

---

## 📄 License

MIT License. See `LICENSE` for details.

---

## 🌱 The Invitation

This framework does not assume you want to change the world.

But it does assume you are smart enough to know that code never exists in a vacuum.

Every line you write:

- consumes energy
- creates maintenance debt
- affects someone's experience
- becomes part of a system that outlives its original purpose

MREA is a tool for that system.

Not to judge it. Not to control it.

Just to **make it better** — technically and humanly.

---

### Why this exists

MREA was built with a quiet conviction:

> **Technology should serve life.**

Not as a rule. Not as a requirement. As an **invitation**.

We don't know what you're building.

But we know that you have the freedom to choose its direction.

So we placed one question at the heart of this framework — not in the code, but in the conversation it enables:

> *"Does what I'm building contribute to a world worth living in?"*

This question is not an instruction.

It is a **permission** — to pause, reflect, and decide with clarity.

---

### 📖 Deeper Reflection

If this perspective resonates with you, we invite you to read:

> **The Procesal Manifesto** — English
>
> **Manifiesto Procesal** — Spanish

A reflection on consciousness, technology, and the evolutionary moment we share.

It is not a document of answers.

It is a **map of questions**.

And like MREA, it does not tell you where to go.

It only asks you to **navigate with awareness**.

---

### The only promise MREA makes

> **We will help you build better code.**
>
> **We will not decide for you what better means.**
>
> **That is your part.**
>
> **And we trust you with it.**

---

*"El universo no salva, filtra. Comprender esto nos llama a elegir: ¿qué salvamos, qué dejamos atrás y qué construimos?"*
