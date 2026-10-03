---
name: evidence-based-validation
description: Every claim, finding, and decision must be supported by verifiable evidence. No assumptions. No speculation.
---

# Evidence-Based Validation

## Core Principle

> **No claims without evidence.**

Everything in MREA — architecture decisions, audit findings, implementation changes — must be backed by verifiable evidence.

---

## When to apply this skill

Apply this skill to **every MREA agent** in every phase of the workflow:

- Discovery
- Architecture
- Audit
- Implementation
- Validation

---

## Types of Evidence

| Type | Examples |
| :--- | :--- |
| **Code** | Functions, classes, files, modules, types, interfaces |
| **Configuration** | `package.json`, `tsconfig.json`, `.env.example`, CI/CD configs |
| **Documentation** | README, ADRs, API docs, architecture documents |
| **Tool Output** | Compilation results, lint output, test results, build logs |
| **User Input** | Explicit requirements, clarification answers, approval messages |

---

## What Evidence Is Not

- "I think this is how it works"
- "It probably should do X"
- "I assume the function exists"
- "This is likely the cause"
- "In my experience..."

---

## The Evidence Standard

| Claim | Required Evidence |
| :--- | :--- |
| "Function X exists" | `grep` result showing the function definition |
| "API endpoint Y is used" | Call sites found in code |
| "This change breaks compatibility" | Comparison of old vs new contract with call sites |
| "The build passes" | Actual build output |
| "Tests pass" | Test runner output |
| "Security is intact" | Security review with specific checks |
| "The root cause is Z" | Trace of execution flow or data flow |

---

## When Evidence Is Incomplete

If evidence is insufficient:

```text
CLASSIFY AS: UNKNOWN
STATUS: BLOCKED
ACTION: Request information from user or gather more evidence.
```

Never convert:

```text
UNKNOWN → SAFE
```

---

## Example

**Claim**: "The `calculateTotal()` function has a bug."

**Evidence Required**:
1. Code of `calculateTotal()`.
2. Test that fails (or manual execution result).
3. Expected output vs actual output.
4. Root cause traced.

**Unacceptable**: "It's probably a rounding error."