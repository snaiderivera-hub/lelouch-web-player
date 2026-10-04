
Each dimension contributes 0–2 points.

**Total range:** 0–10

---

## Risk Classification

| Score | Classification | Description |
|-------|----------------|-------------|
| 0–2 | **Low** | Trivial, localized, fully reversible, no security impact |
| 3–5 | **Medium** | Moderate scope, some unknowns, manageable risk |
| 6–8 | **High** | Significant impact, security-sensitive, cross-system, or difficult rollback |
| 9–10 | **Critical** | Irreversible, high blast radius, critical business function |

---

## Workflow Mapping

| Classification | Material? | Workflow |
|----------------|-----------|----------|
| **Low (0–2)** | No | **Fast Path** — Implementation + Human Approval |
| **Low (0–2)** | Yes | Standard Architecture + Human Approval |
| **Medium (3–5)** | Yes | Architecture + Recommended Audit + Human Approval |
| **High (6–8)** | Yes | Architecture + Mandatory Audit + Human Approval |
| **Critical (9–10)** | Yes | Advanced Architecture + Mandatory Audit + Explicit Risk Acceptance |

---

## Material Change Definition

A change is **material** if it affects any of the following:

- Critical behavior
- Security
- Cross-system integrations
- Data migrations
- Public contracts
- Difficult or irreversible rollback

For non-material changes (e.g., typo fixes, label changes), the Orchestrator may apply the **Fast Path** at its discretion, provided the change is demonstrably non-material.

---

## Examples

| Request | Impact | Blast | Reversibility | Security | Uncertainty | Score | Classification |
|---------|--------|-------|---------------|----------|-------------|-------|----------------|
| Fix typo in button label | 0 | 0 | 0 | 0 | 0 | 0 | **Low** |
| Add new optional field to API | 1 | 1 | 1 | 0 | 1 | 4 | **Medium** |
| Migrate user database schema | 2 | 2 | 2 | 1 | 2 | 9 | **Critical** |
| Add JWT authentication to existing service | 2 | 2 | 1 | 2 | 1 | 8 | **High** |
| Update dependency version | 1 | 1 | 0 | 0 | 1 | 3 | **Medium** |

---

## Governance Rules

1. **The Orchestrator** is responsible for classifying every request using this model.
2. **In case of doubt**, the Orchestrator must choose the higher classification.
3. **Security-related uncertainty** must be treated as HIGH until resolved.
4. **Material changes** require mandatory audit regardless of score.

---

## Version

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-08-24 | Initial release |
