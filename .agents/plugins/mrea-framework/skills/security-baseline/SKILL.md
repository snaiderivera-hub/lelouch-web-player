---
name: security-baseline
description: Minimum security review for any code change. Authentication, authorization, secrets, data exposure, and validation.
---

# Security Baseline

## Core Principle

> **Security is not optional.**

Every change must be reviewed for security impact. Critical security uncertainty blocks progression.

---

## When to apply this skill

Apply this skill to **any architecture proposal** and **any implementation task** that touches:

- authentication
- authorization
- user data
- secrets
- API endpoints
- data persistence
- external integrations
- public interfaces

---

## Required Security Checks

For every change, evaluate:

### 1. Authentication

- Is authentication required for this endpoint/function?
- If yes, is it properly enforced?
- If no, should it be?

### 2. Authorization

- Are permissions checked before accessing resources?
- Is the user allowed to perform this action?
- Is tenant/data isolation maintained?

### 3. Secrets Handling

- Are secrets hardcoded anywhere?
- Are secrets exposed in logs or error messages?
- Are secrets stored securely (env variables, vault)?

### 4. Input Validation

- Are user inputs validated?
- Are inputs sanitized before use?
- Are there injection risks (SQL, XSS, command injection)?

### 5. Data Exposure

- Is sensitive data exposed in logs?
- Is sensitive data exposed in responses?
- Is sensitive data exposed in URLs?

---

## Severity Levels

| Severity | Definition | Action |
| :--- | :--- | :--- |
| **CRITICAL** | Authentication bypass, data exposure, injection vulnerability | **BLOCK** |
| **HIGH** | Missing authorization, insecure defaults | **BLOCK** |
| **MEDIUM** | Incomplete validation, missing logs | Resolve or document |
| **LOW** | Minor non-compliance | Document |

---

## When Evidence Is Incomplete

If security evidence is incomplete:

```text
CLASSIFY AS: UNKNOWN
STATUS: BLOCKED
ACTION: Request more information or perform deeper analysis.
```

Never convert:

```text
UNKNOWN → SAFE
```

---

## Example

**Input**: "Implement a new API endpoint `/api/users/me` to return the current user's profile."

**Security Baseline applied**:

1. **Authentication**: Is authentication required? Yes. Is it enforced? JWT middleware is already in place and the route is protected. ✅
2. **Authorization**: Does the endpoint return the correct user? Ensure the user ID from the token matches the requested resource (the authenticated user). ✅
3. **Secrets**: No secrets involved.
4. **Input Validation**: No user input besides the auth token.
5. **Data Exposure**: Check the response schema — ensure the password hash and email verification flags are excluded. ✅

**Outcome**: APPROVED WITH MINOR CHANGES — Add an integration test to verify the authorization boundary (e.g., user A cannot access user B's data).