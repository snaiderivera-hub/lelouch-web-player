# MREA Skill Catalog

This catalog centralizes all available skills for the MREA framework in Lelouch IPTV & Web Player.

Skills are reusable, autonomous guidelines that agents apply based on the task domain.

---

## Available Skills

| Skill | Domain | When to apply |
| :--- | :--- | :--- |
| `engineering-protocol` | Engineering | Modifying existing code, fixing bugs, adding features, refactoring |
| `ponytail-philosophy` | Engineering | Any architecture proposal or implementation task (default mindset) |
| `happy-path` | UX/UI | Any UX/UI design or frontend implementation |
| `evidence-based-validation` | Validation | Every agent, every phase (no claims without evidence) |
| `security-baseline` | Security | Changes affecting auth, data, APIs, or external integrations |
| `rollback-strategy` | Operations | High-impact changes, migrations, or public API modifications |
| `iptv-architect` | Domain IPTV | Streaming, Xtream Codes API, M3U generator, Exoplayer, HLS, EPG |

---

## How Agents Should Use Skills

1. **Identify the domain** of the task (e.g., "This is a code modification" → Engineering).
2. **Load the relevant skills** from this catalog.
3. **Apply all applicable skills** in parallel (they are complementary, not mutually exclusive).

Antigravity operates as the **Enterprise Orchestrator** and must verify skill compliance across all stages.
