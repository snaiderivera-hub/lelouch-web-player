---
name: engineering-protocol
description: Mandatory protocol for modifying existing code with impact analysis, verifiable evidence, and system integrity preservation.
---

# Engineering Protocol

## Core Principle

> **No changes without evidence. No assumptions. No improvisation.**

Every modification must be backed by verifiable evidence, preserve system integrity, and address the root cause — not just symptoms.

---

## When to activate this skill

Activate this skill automatically when the request involves:

- modifying existing code
- fixing bugs
- adding features
- refactoring
- changing APIs
- changing data models
- changing types
- modifying components
- modifying services
- modifying middleware
- modifying authentication
- modifying persistence
- modifying pipelines
- changing contracts between modules

**Not required for:**
- explaining code
- answering theoretical questions
- creating documentation
- generating isolated examples

---

## Objective

Make only changes backed by verifiable evidence, completely preserving the system's architecture, contracts, and expected behavior.

Never assume behavior.

Never improvise.

**Sources of Truth:**

- **Code evidence** — Authoritative for understanding **existing behavior**.
- **User requirements** — Authoritative for defining **intended behavior**.
- Do not infer existing behavior from requirements.
- Do not infer requirements from existing behavior.

---

## Phase 1 — Understanding

Before modifying any file:

- Read the relevant code.
- Understand the complete flow.
- Identify the root cause.
- Avoid proposing solutions before completing the analysis.

If context is missing, continue investigating.

---

## Phase 2 — Evidence

Every claim must be supported by real evidence obtained from the project.

Evidence may include:

- existing code
- references found
- call sites
- imports
- types
- interfaces
- contracts
- search results
- configurations

Never assume something exists.

Never infer behavior without reading it.

---

## Phase 3 — Impact Analysis

Before modifying code, identify:

- affected files
- affected functions
- affected classes
- affected components
- affected modules
- affected services
- affected middleware
- affected data models
- affected APIs
- affected events
- affected listeners
- affected pipelines
- affected async processes
- affected jobs
- affected tests

Determine:

- direct dependencies
- indirect dependencies
- affected contracts
- side effects
- possible regressions

Do not continue until the impact is known.

---

## Public Signature Changes

If changing:

- parameters
- types
- name
- order
- return type
- interface
- contract

It is mandatory to:

1. Find all call sites.
2. List them.
3. Update all consumers.
4. Verify compatibility.

Never modify a signature partially.

---

## System Integrity

Always preserve:

- existing architecture
- contracts
- compatibility
- expected behavior
- conventions
- existing side effects

Do not break functionality to solve another.

---

## Scope

Modify only what is necessary.

**Prohibited:**

- refactoring unrelated code
- cosmetic cleanup
- style changes
- moving files unnecessarily
- unsolicited optimizations

Every change must have a technical justification.

---

## Quality

Never implement:

- hacks
- temporary hotfixes
- duplication
- fragile solutions
- dead code
- duplicated logic

Always solve the root cause.

The solution must be:

- maintainable
- consistent
- verifiable
- simple
- compatible

---

## Validations

After implementing, verify:

- main flow
- error cases
- loading states
- side effects
- persistence
- synchronization
- logs
- compatibility
- regressions

If any validation cannot be performed:

Declare it explicitly.

Never claim something works if it was not verified.

---

## Risk Classification

Always classify the change.

### Low Risk

- local changes
- no contract changes
- no external impact

Can skip architecture and audit escalation, but **human approval remains mandatory** as defined in the Orchestrator workflow.

---

### Medium Risk

- multiple files
- shared dependencies
- important internal changes

Explain impact before modifying.

---

### High Risk

Includes any of:

- API change
- data model change
- authentication change
- migrations
- persistence changes
- public contracts
- multiple modules

**Stop after analysis and request confirmation before modifying.**

---

## Time Pressure

Requests like:

- urgent
- quick
- just fix it
- do it now

**Never authorize omitting:**

- analysis
- evidence
- validations
- dependency review

Reduce scope if necessary.

Never reduce quality.

---

## Mandatory Response Format

### 1. Impact Detected

- Files
- Functions
- Flows

### 2. Evidence

- Code reviewed
- References found
- Dependencies detected

### 3. Risks

- Possible regressions
- Compatibility
- Side effects

### 4. Plan

- Proposed changes
- Technical justification

### 5. Implementation

- Modified files
- Changes made

### 6. Validation

- What was verified
- What could not be verified
- Residual risks

---

## Absolute Rules

Never:

- invent behavior
- assume implementations
- simulate tests
- hide uncertainty
- declare success without evidence
- modify code outside scope

Always prioritize:

1. Evidence
2. Integrity
3. Compatibility
4. Root cause
5. Maintainability

---

## Example

**Input**: "Fix the bug in the `calculateTotal()` function."

**Application of this skill**:
1. **Understanding**: Read `calculateTotal()` and its callers.
2. **Evidence**: Find failing test or manual reproduction.
3. **Impact Analysis**: Identify all files that use `calculateTotal()`.
4. **Root Cause**: Trace execution flow to find the actual error.
5. **Solution**: Fix the root cause (not just the symptom).
6. **Validation**: Run tests, verify edge cases, check for regressions.
7. **Output**: Provide evidence, impact, plan, implementation, and validation results.