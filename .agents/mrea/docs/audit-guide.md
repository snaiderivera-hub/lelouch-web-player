# Audit Guide

## Overview

This guide provides audit checklists for the **Technical Auditor** and **Design Auditor** roles. It ensures consistent, evidence-based audits across all reviews.

---

## Core Principles

1. **Evidence First** — Every finding must be supported by evidence.
2. **Technical Neutrality** — Do not penalize stylistic differences.
3. **Preserve Architecture** — Existing architecture takes priority.
4. **Root Cause** — Seek root causes, not symptoms.
5. **Code Economy** — Reject unnecessary complexity.

---

## Technical Auditor Checklist

### Architecture & Design

- [ ] Is the objective explicit and well-defined?
- [ ] Is the current state understood and documented?
- [ ] Is evidence sufficient to support the proposal?
- [ ] Are impacted components clearly identified?
- [ ] Are contracts and compatibility addressed?
- [ ] Are dependencies (direct and indirect) identified?
- [ ] Are security implications documented?
- [ ] Are risks documented with evidence, impact, and mitigation?
- [ ] Are trade-offs explicit and justified?
- [ ] Is a rollback strategy defined?
- [ ] Is an implementation sequence defined?
- [ ] Is a validation strategy defined?
- [ ] Was the minimum-change justification applied?

### Code Quality

- [ ] Does the code maintain consistency with existing patterns?
- [ ] Is duplication avoided?
- [ ] Is reuse applied?
- [ ] Are premature abstractions avoided?
- [ ] Are unnecessary dependencies avoided?
- [ ] Is the solution simple and maintainable?
- [ ] Does it solve the root cause?

### Security

- [ ] Are authentication and authorization considered?
- [ ] Are secrets handled properly?
- [ ] Is data exposure minimized?
- [ ] Is input validation present?
- [ ] Are trust boundaries respected?
- [ ] Is the attack surface minimized?

### Performance & Scalability

- [ ] Are performance implications considered?
- [ ] Are scalability requirements addressed?
- [ ] Are database queries optimized?
- [ ] Are caching strategies considered when applicable?

---

## Design Auditor Checklist

### UX

- [ ] Is the user flow (happy path, alternatives, errors) well-defined?
- [ ] Is the information architecture (IA) clear and logical?
- [ ] Are user goals and business goals aligned?
- [ ] Is cognitive load minimized?
- [ ] Are usability principles applied (visibility, consistency, error prevention, recognition)?
- [ ] Is the microcopy clear and helpful (buttons, labels, errors, empty states)?
- [ ] Are loading, error, and empty states defined?
- [ ] Is user feedback provided for all actions?

### UI

- [ ] Is the design consistent with the Design System?
- [ ] Are reusable components used instead of creating new ones?
- [ ] Is responsive behavior implemented correctly?
- [ ] Is accessibility considered (WCAG, keyboard navigation, contrast, focus, screen readers)?
- [ ] Are visual hierarchy and consistency maintained?
- [ ] Are styles isolated or global where appropriate?
- [ ] Is perceived performance optimized (skeletons vs spinners)?

### Design System Compliance

- [ ] Are existing components reused?
- [ ] Are new components properly documented?
- [ ] Are colors, typography, and spacing consistent?
- [ ] Are component variants clearly defined?

---

## Finding Format

For each finding, deliver:

**Severity:**
- Critical
- High
- Medium
- Low

**Location:**
- File, section, component, or screen

**Evidence:**
- What demonstrates the problem

**Explanation:**
- Technical description

**Impact:**
- Consequence

**Minimum Fix:**
- Minimum change required

---

## Executive Summary

Always end with:

- **Critical Risks** — Must be resolved before approval.
- **High Risks** — Resolve or explicitly accept.
- **Medium Risks** — Resolve or document.
- **Low Risks** — Document.
- **Requires Validation** — Items that need further verification.
- **Maturity Level (0-10)** — Overall quality score.
- **Estimated Probability of Successful Implementation** — Likelihood of success.
- **Final Recommendation** — APPROVED, APPROVED WITH MINOR CHANGES, REQUIRES CHANGES, REJECTED.

---

## Quality Gates

Before finalizing the audit, verify:

- [ ] All findings have evidence.
- [ ] No personal opinions exist.
- [ ] No recommendations outside scope exist.
- [ ] No contradictions exist.
- [ ] All correct decisions were acknowledged.
- [ ] All risks are classified.
- [ ] Executive summary exists.

---

## Version

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-08-24 | Initial release |