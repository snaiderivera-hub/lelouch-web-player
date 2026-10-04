# Model Routing Recommendations

## Overview

MREA separates **reasoning cost** from **execution cost**.

- **High-cost reasoning models** — Used for architecture, complex analysis, and independent audits.
- **Cost-efficient execution models** — Used for implementation, mechanical changes, and focused code generation.

This allows model routing based on the actual complexity of the task instead of blindly assigning the most expensive model to everything.

---

## Recommended Model Assignments (Production-Tested)

| Agent | Recommended Model | Reasoning Effort | Text Verbosity | Temperature |
|-------|-------------------|------------------|----------------|-------------|
| **Orchestrator** | `deepseek-v4-flash` | `max` | `low` | `0.5` |
| **Software Architect** | `hy3` | `high` | `low` | `0.0` |
| **Advanced Software Architect** | `glm-5.2` | `max` | `low` | `0.0` |
| **Design Architect** | `mimo-v2.5-pro` | _(default)_ | _(default)_ | `0.1` |
| **Technical Auditor** | `deepseek-v4-pro` | `max` | `low` | `0.0` |
| **Design Auditor** | `deepseek-v4-pro` | `max` | `low` | `0.1` |
| **Implementer** | `deepseek-v4-flash` | `max` | `low` | `0.0` |

---

## Justification

| Agent | Reasoning | Execution | Why |
|-------|-----------|-----------|-----|
| **Orchestrator** | Moderate | High | Needs fast, accurate routing decisions. |
| **Software Architect** | High | Low | Requires deep architectural reasoning. |
| **Advanced Software Architect** | Highest | Low | Requires maximum reasoning for critical changes. |
| **Design Architect** | Moderate | Low | Balances UX reasoning with cost. |
| **Technical Auditor** | High | Low | Requires rigorous validation and detection. |
| **Design Auditor** | High | Low | Requires rigorous UX/UI validation. |
| **Implementer** | Moderate | High | Needs fast, precise code generation. |

---

## How to Configure in OpenCode

In each agent's frontmatter, uncomment the recommended configuration:

```yaml
# model: "opencode-go/deepseek-v4-flash"  # Recommended for orchestrator
# reasoningEffort: "max"                  # Optional: adjust as needed
# textVerbosity: "low"                    # Optional: adjust as needed
# temperature: 0.5                        # Optional: adjust as needed
```

---

## Customization

These are **recommendations only**. You can substitute any model that fits your budget and quality requirements.

Consider:

- **Budget** — Higher reasoning models cost more.
- **Latency** — Higher reasoning models are slower.
- **Quality** — Higher reasoning models produce better results for complex tasks.
- **Task complexity** — Simple tasks don't need the most expensive model.

---

## Cost Optimization

| Task Type | Model Tier | Cost Tier |
|-----------|------------|-----------|
| Direct response | Flash | Low |
| Discovery | Flash | Low |
| Standard Architecture | Mid-tier | Medium |
| Advanced Architecture | Premium | High |
| Audit | Premium | High |
| Implementation | Flash | Low |

MREA routes tasks to the appropriate model tier automatically via the Orchestrator.

---

## Version

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-08-24 | Initial release |