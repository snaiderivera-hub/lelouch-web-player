---
name: ponytail-philosophy
description: Core principle: code is a liability. Eliminate before creating. Reuse before writing. Simplify before abstracting.
---

# Ponytail Philosophy

## Core Principle

> **Code is a liability.**

Every new line of code creates something that must eventually be:

- maintained
- tested
- debugged
- secured
- documented
- understood

Therefore, the best code is the code that is never written.

---

## When to apply this skill

Apply this skill to **any architecture proposal** and **any implementation task**.

It is the default mindset for all MREA agents.

---

## The Evaluation Order

Before proposing or writing any new code, evaluate in this exact order:

```text
DOES THE REQUIRED BEHAVIOR ALREADY EXIST IN THE SYSTEM?
        ↓ NO
CAN THE PROBLEM BE SOLVED BY CONFIGURATION OR ENVIRONMENT SETTINGS?
        ↓ NO
CAN THE REQUIREMENT BE SIMPLIFIED OR ELIMINATED?
        ↓ NO
CAN AN EXISTING COMPONENT, FUNCTION, OR SERVICE BE REUSED?
        ↓ NO
CAN AN EXISTING ABSTRACTION BE EXTENDED INSTEAD OF CREATING A NEW ONE?
        ↓ NO
IS THE NEW CODE STRICTLY THE MINIMUM NECESSARY?
```

If any of the first five answers is **YES**, the solution must use that approach instead of creating new code.

---

## What to Reject

Reject proposals that introduce:

- premature abstractions
- unnecessary wrappers
- duplicate functionality
- unjustified dependencies
- speculative extensibility ("we might need this later")
- complexity without demonstrated value
- new patterns when existing ones are sufficient
- new components when existing ones can be extended

---

## What to Prefer

Prefer solutions that:

- reuse existing code
- extend existing components
- use configuration over code
- reduce the total number of files
- remove unused or dead code
- simplify existing complexity
- reduce dependencies

---

## When New Code Is Justified

New code is justified only when:

1. The behavior does not exist in the system.
2. Configuration or simplification cannot solve it.
3. No existing component can be reused or extended.
4. The new code is the **minimum necessary** to satisfy the requirement.

---

## Example

**Input**: "Create a new helper function to format dates for the dashboard."

**Ponytail evaluation**:
1. Does the behavior already exist? → Checked: `date-utils.ts` has `formatDate()`.
2. Can the problem be solved with configuration? → The existing function supports the required format with options.
3. Is new code needed? → **No.** Use existing function with configuration.

**Correct output**: "Reuse `formatDate()` from `date-utils.ts` with the following options: ..."