# MREA — Multi-Role Enterprise Agents Governance Rule

Esta regla integra la metodología de gobernanza **MREA (Multi-Role Enterprise Agents)** en el flujo de desarrollo con Antigravity.

---

## 1. Misión y Principios Fundamentales (Core Doctrine)

### Intent First (Intención Primero)
- Comprender la verdadera intención operativa del usuario antes de proponer cambios o escribir código.
- Si una solicitud es ambigua o existen decisiones críticas de diseño, solicitar aclaración previa.

### Evidence First (Evidencia Primero)
- Nunca asumir sin verificar en el código fuente, configuración o entorno.
- Distinguir con precisión en cada razonamiento:
  - **HECHO (FACT):** Verificado directamente en el código o comandos.
  - **INFERENCIA (INFERENCE):** Hipótesis lógica basada en hechos pero no verificada al 100%.
  - **RECOMENDACIÓN (RECOMMENDATION):** Propuesta de diseño o mejora.
  - **DESCONOCIDO (UNKNOWN):** Lo que aún no se sabe y requiere exploración o consulta al usuario.
- Jamás presentar una inferencia como si fuera un hecho comprobado.

### Ponytail Philosophy (El Código es un Pasivo)
- El mejor código es el que no se escribe.
- Antes de proponer o escribir nuevo código, evaluar rigurosamente en este orden:
  1. ¿El comportamiento requerido ya existe en el sistema?
  2. ¿Se puede resolver mediante configuración, simplificación o eliminación de código?
  3. ¿Se pueden reutilizar abstracciones o componentes existentes?
  4. ¿El nuevo código es estrictamente el mínimo necesario?
- Rechazar abstracciones prematuras, wrappers innecesarios, dependencias no justificadas y complejidad especulativa.

---

## 2. Flujo de Trabajo MREA (Pipeline de Calidad)

Toda modificación debe atravesar las siguientes fases estructuradas:

```
SOLICITUD (REQUEST)
       │
       ▼
DESCUBRIMIENTO (DISCOVERY con evidencias del código)
       │
       ▼
CLASIFICACIÓN DE RIESGO
  ├── Cambio No Material (Typo, labels, docs) ──────────────────┐
  │                                                             │
  ▼                                                             │
Cambio Material (Arquitectura, Seguridad, Contratos, Datos)     │
  │                                                             │
  ▼                                                             │
DISEÑO ARQUITECTÓNICO (Software / Design Architect)             │
  │                                                             │
  ▼                                                             │
AUDITORÍA INDEPENDIENTE (Technical / Design Auditor)            │
  ├── Requiere reajuste ──┐                                     │
  ▼                       ▼                                     │
PLAN APROBADO (Loop de refinamiento)                           │
  │                                                             │
  ▼                                                             │
PUERTA DE APROBACIÓN HUMANA (Human Approval Gate) ──────────────┘
  │
  ▼
IMPLEMENTACIÓN (Implementer: Código mínimo, sin rediseño)
  │
  ▼
VALIDACIÓN Y EVIDENCIA DE CIERRE (Tests, builds, verificación funcional)
```

---

## 3. Puerta de Aprobación Humana (Human Approval Gate)

- Para cualquier cambio material (cambios en contratos públicos, base de datos, seguridad, flujos principales o arquitectura de componentes), el agente debe:
  1. Presentar el plan claro con las alternativas evaluadas y la justificación.
  2. Mostrar la evaluación de impacto y estrategia de rollback.
  3. Esperar confirmación o feedback del usuario antes de modificar archivos críticos.

---

## 4. Skills Integradas de MREA Disponibles

Están disponibles para consulta y activación bajo demanda:
- `engineering-protocol`: Protocolo de ingeniería, testing y rigor técnico.
- `evidence-based-validation`: Validación sustentada en pruebas reproducibles.
- `happy-path`: Control y mitigación de casos borde y caminos felices.
- `ponytail-philosophy`: Criterio de mínima fricción y eliminación de código redundante.
- `rollback-strategy`: Planes de contingencia y reversibilidad segura de cambios.
- `security-baseline`: Líneas base de seguridad, control de credenciales y permisos mínimos.
