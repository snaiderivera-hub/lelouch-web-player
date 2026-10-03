---
name: happy-path
description: UX principle: prioritize the main user flow. Design for the 80% use case first. Handle edge cases but never compromise the primary experience.
---

# Happy Path First

## Core Principle

> **Design for the happy path. Everything else is secondary.**

The happy path is the most common, most important user journey. It is what the user comes to do. Everything else — edge cases, error states, alternative flows — must not compromise the primary experience.

---

## When to apply this skill

Apply this skill to **any UX/UI design** and **any frontend implementation**.

It is the default mindset for all Design-related agents.

---

## The Design Order

Before designing any interface, establish:

### 1. The Primary User Goal

What is the single most important thing the user needs to accomplish?

Example: "The user needs to submit an expense report."

### 2. The Happy Path

The simplest, shortest, most direct route to that goal.

Example: "Open dashboard → Click 'New Report' → Fill fields → Submit."

### 3. The Secondary Paths

Alternative routes that still lead to the goal (e.g., "Save as draft" → "Complete later").

### 4. The Edge Cases

What happens when something goes wrong? (e.g., "Network error", "Invalid data", "Missing permissions").

---

## Priority Rules

| Priority | Element | Effort |
| :--- | :--- | :--- |
| 1 | Happy path | 100% |
| 2 | Secondary paths | 80% |
| 3 | Error states | 60% |
| 4 | Empty states | 40% |
| 5 | Edge cases | 20% |

The happy path must be **flawless** before any other state is designed.

---

## What to Reject

Reject designs that:

- prioritize edge cases over the main flow
- add friction to the happy path to handle rare scenarios
- require extra clicks or steps for the primary task
- hide primary actions behind secondary menus
- overcomplicate simple tasks

---

## What to Prefer

Prefer designs that:

- make the primary action visible and obvious
- reduce the number of steps to complete the main task
- guide the user naturally through the happy path
- provide clear feedback after each step
- allow the user to complete the task without reading instructions

---

## Example

**Input**: "Design a checkout flow for an e-commerce app."

**Happy Path First approach**:
1. **Primary goal**: User completes a purchase.
2. **Happy path**: Cart → Shipping → Payment → Confirmation.
3. **Secondary paths**: Apply promo code, save for later.
4. **Edge cases**: Payment fails, address invalid, out of stock.

**Design output**:
- Happy path: 3 screens, clear progress indicators, minimal friction.
- Edge cases: Handled but never interrupt the primary flow (e.g., validation after fields, not before).