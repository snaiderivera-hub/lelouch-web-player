# Security Model

## Purpose

This document defines the security principles, permission boundaries, and access controls for the MREA framework.

It ensures that each agent operates with the least privilege necessary to fulfill its role.

---

## Core Principles

1. **Least Privilege** — Each agent has only the permissions required for its function.
2. **Separation of Duties** — No agent can both design and implement without oversight.
3. **Human Approval Gate** — Implementation requires explicit human authorization.
4. **Auditability** — All significant actions are documented and reviewable.
5. **Credential Safety** — Secrets are never exposed to agents.

---

## Permission by Role

| Role | Read | Write | Execute | Task Delegation | Notes |
|------|------|-------|---------|-----------------|-------|
| **Orchestrator** | ✅ All files | ❌ Denied | ❌ Denied | ✅ Allowed (subagents) | Control plane only |
| **Software Architect** | ✅ Code, docs | ❌ Denied | ❌ Denied | ❌ Denied | Analysis only |
| **Advanced Software Architect** | ✅ Code, docs | ❌ Denied | ❌ Denied | ❌ Denied | Deep analysis only |
| **Design Architect** | ✅ Code, UI assets | ❌ Denied | ❌ Denied | ❌ Denied | Analysis only |
| **Technical Auditor** | ✅ Code, plans | ❌ Denied | ❌ Denied | ❌ Denied | Review only |
| **Design Auditor** | ✅ Code, designs | ❌ Denied | ❌ Denied | ❌ Denied | Review only |
| **Implementer** | ✅ Code, plans | ✅ After approval | ✅ Build/test only | ❌ Denied | Most privileged, but bounded |

---

## File Access Restrictions

| File Type | Read | Write | Notes |
|-----------|------|-------|-------|
| Source code | ✅ All agents | ✅ Implementer only (after approval) | — |
| Configuration files | ✅ All agents | ✅ Implementer only (after approval) | Includes `.env.example`, `package.json`, etc. |
| Secrets (`.env`, `.env.*`) | ❌ Denied | ❌ Denied | Never exposed to any agent |
| Documentation | ✅ All agents | ✅ Implementer only (after approval) | — |
| Plans and designs | ✅ All agents | ✅ Architects, Auditors | Artifacts of workflow |

---

## Credential Handling

1. **Never** expose secrets in prompts, logs, or delegation contexts.
2. **Never** read `.env` or `.env.*` files.
3. **Use environment variables** for runtime secrets; never hardcode them.
4. **Do not** copy credential values between contexts.
5. **If a credential is accidentally exposed**, notify the user immediately and block the workflow.

---

## Audit Trail

Every significant action must be recorded:

| Event | Recorded By | Content |
|-------|-------------|---------|
| Request received | Orchestrator | Request ID, user, timestamp |
| Delegation to agent | Orchestrator | Agent name, task, timestamp |
| Gate result (PASS/FAIL) | Orchestrator | Gate name, result, evidence |
| Human approval | Human (user) | Approval text, timestamp |
| Implementation | Implementer | Files modified, timestamp |
| Security finding | Auditor | Finding ID, severity, location |

---

## Security Precedence

When a security finding is classified as **HIGH**, this classification overrides any other severity consideration.

- Security HIGH → **BLOCK**
- Cannot be resolved with "explicit acceptance" unless the human explicitly accepts the risk in a separate document.
- Risk acceptance is not the same as implementation approval.

---

## OpenCode Adapter Permissions

The OpenCode adapter enforces these permissions via frontmatter:

| Agent | `edit` | `bash` | `task` (subagents) |
|-------|--------|--------|---------------------|
| `enterprise-orchestrator` | `deny` | `deny` | Allowed (list) |
| `enterprise-architect` | `deny` | `deny` | Denied |
| `enterprise-advanced-architect` | `deny` | `deny` | Denied |
| `enterprise-design-architect` | `deny` | `deny` | Denied |
| `enterprise-auditor` | `deny` | `deny` | Denied |
| `enterprise-design-auditor` | `deny` | `deny` | Denied |
| `enterprise-implementer` | `allow` | `ask` | Denied |

---

## Security Violations

The workflow must **BLOCK** immediately if:

1. A secret is exposed.
2. An agent attempts to read a secret file.
3. An agent attempts to bypass approval.
4. An agent attempts to modify files without permission.
5. A security finding is classified as HIGH and not resolved.

---

## Version

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-08-24 | Initial release |