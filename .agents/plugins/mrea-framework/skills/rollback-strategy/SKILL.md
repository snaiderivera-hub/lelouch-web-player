---
name: rollback-strategy
description: Plan for reverting changes. For high-impact changes, define what, how, and in what order to roll back.
---

# Rollback Strategy

## Core Principle

> **Every change must be reversible.**

If a change cannot be reversed, it requires additional justification and approval.

---

## When to apply this skill

Apply this skill to **any architecture proposal** or **implementation task** that:

- affects production data
- modifies public APIs
- changes authentication or authorization
- modifies infrastructure
- involves database migrations
- affects multiple modules

---

## Rollback Requirements

For every change, define:

### What Can Be Reverted

- Code changes
- Configuration changes
- Schema changes
- Data changes
- Infrastructure changes

### What Cannot Be Reverted

- Irreversible data migrations
- Data loss (permanent deletions)
- Public API removals (without deprecation)

### Order of Rollback

What must happen first, second, third?

Example:
1. Revert code
2. Revert configuration
3. Revert schema
4. Restore data

---

## Reversibility Classification

| Classification | Definition |
| :--- | :--- |
| **Reversible** | Can be reverted without side effects. |
| **Partially Reversible** | Some effects can be reverted; others require manual fixes. |
| **Difficult to Reverse** | Requires significant effort or downtime. |
| **Irreversible** | Cannot be reverted without data loss or breaking changes. |

Decisions classified as **Difficult to Reverse** or **Irreversible** require additional justification and approval.

---

## Rollback Plan Format

```text
## Rollback Plan

### What to revert:
- File changes (list)
- Configuration changes (list)
- Database changes (list)

### Rollback order:
1. Revert code changes
2. Revert configuration changes
3. Revert database changes (if reversible)
4. Restore data (if applicable)

### Impact of rollback:
- Downtime expected
- Data loss possible
- User impact

### Validation after rollback:
- Verify system returns to previous state
- Verify no residual effects
- Verify no data corruption

### Limitations:
- What cannot be reverted
- What requires manual intervention
```

---

## Example

**Input**: "Add a new `is_active` column to the `users` table and expose it in the public API."

**Rollback Strategy applied**:

1. **What can be reverted**:
   - Code changes (API endpoint modifications)
   - Configuration changes (if any)
   - Schema changes (drop the `is_active` column)
   - Data changes (the column can be dropped; no data loss if no other table depends on it)

2. **What cannot be reverted**:
   - Any client code that starts depending on the new field before the rollback.

3. **Rollback order**:
   1. Revert API code to remove the field from responses.
   2. Remove the column from the database (ALTER TABLE DROP COLUMN).

4. **Impact of rollback**:
   - No data loss.
   - Clients who already consumed the field will break if they don't handle missing fields gracefully.

5. **Validation after rollback**:
   - Verify the API no longer returns the field.
   - Verify the database column is removed.
   - Verify no residual errors in logs.

6. **Limitations**:
   - If any client code shipped that depends on the new field, it will fail. This must be communicated before the rollback.