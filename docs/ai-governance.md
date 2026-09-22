# Gobernanza operativa de agentes de IA

Este documento define cómo coordinamos agentes en PrintVerse. Las invariantes del dominio, las restricciones generales y la matriz de gates están en [`AGENTS.md`](../AGENTS.md); aquí se describe el proceso humano y los handoffs.

## Principios operativos

- Una persona mantiene la autoridad sobre Git, integración, releases y decisiones de producto.
- Cada agente recibe un objetivo, alcance, criterios de aceptación y restricciones explícitos.
- Quien implementa no aprueba su propio cambio; la revisión debe ser independiente.
- Los agentes no amplían el alcance para corregir hallazgos adyacentes.
- Los reportes distinguen hechos verificados, inferencias y supuestos pendientes de confirmar.
- Una validación omitida o bloqueada se reporta como tal, nunca como exitosa.
- La automatización se detiene ante ambigüedad o riesgo; no sustituye decisiones humanas.

## Flujo estándar

```text
Solicitud
  → Planner
  → aprobación humana del plan
  → worktree aislado
  → Builder o Debugger
  → gates
  → Reviewer
  → corrección, si aplica
  → gates nuevamente
  → Reviewer final
  → humano
  → commit y PR
```

El humano puede simplificar el flujo para tareas triviales, pero nunca omite revisión humana en seguridad, datos, concurrencia o contratos públicos.

## Política de worktrees

### Creación y asignación

- Un humano u orquestador autorizado crea la rama y el worktree.
- La ubicación recomendada es `../printverse-worktrees/<task-slug>`.
- Una tarea equivale a una rama y un worktree.
- Cada worktree tiene un único agente escritor: Builder o Debugger.
- Planner y Reviewer operan en modo de solo lectura.
- No se comparte un worktree activo con trabajo humano u otra tarea.

Ejemplo de creación, solo como referencia:

```bash
git fetch origin
git worktree add \
  ../printverse-worktrees/<task-slug> \
  -b <tipo>/<ticket>-<task-slug> \
  origin/main
```

### Comprobaciones antes de editar

El agente escritor confirma:

1. La raíz del repositorio y el worktree asignado.
2. La rama esperada y su base aprobada.
3. Un estado Git limpio al comenzar.
4. La ausencia de archivos o cambios inesperados.
5. El alcance, presupuesto y gates acordados.

Si alguna comprobación falla, el agente se detiene sin manipular los cambios encontrados.

### Restricciones y cierre

- No usar `stash` ni auto-stash.
- No cambiar de rama dentro del worktree.
- No reutilizar el worktree para otra tarea.
- No hacer commit, push, merge, rebase o limpieza desde el agente.
- El humano decide si integra, abandona o conserva el trabajo.
- Solo el humano u orquestador autorizado elimina la rama y el worktree después del cierre.

## Presupuesto de alcance por defecto

Salvo aprobación distinta, cada tarea tiene:

- Un único objetivo.
- Máximo 8 archivos de código o configuración.
- Máximo 400 líneas manuales modificadas.
- Ninguna dependencia nueva.
- Ninguna variable de entorno nueva.
- Ninguna migración.
- Ningún cambio de autenticación o autorización.
- Ningún cambio público de API.
- Ningún refactor oportunista.

Las pruebas cuentan como archivos, pero el Planner puede justificar anticipadamente una excepción razonable de líneas. Seguridad, Flyway, datos, concurrencia, API y dependencias requieren presupuesto explícito y aprobación humana antes de implementar.

## Loop autónomo v1

- Máximo dos ciclos completos `Builder → gates → Reviewer`.
- Solo una ronda de corrección posterior al primer Reviewer.
- Tras corregir, se repiten todos los gates afectados, no solo el que falló.
- Si el mismo gate vuelve a fallar, el agente se detiene y escala.
- Un Reviewer final comprueba el resultado antes del handoff humano.
- Alcanzar el límite no autoriza a omitir gates, reducir pruebas ni ampliar alcance.

## Condiciones de parada

El flujo se detiene y solicita decisión humana cuando:

- Se supera o debe ampliarse el alcance aprobado.
- Existe una decisión de producto no resuelta.
- Hace falta una dependencia, migración o variable no aprobada.
- Aparecen cambios inesperados en el worktree.
- Falla la infraestructura necesaria para validar.
- Los tests son inestables o el fallo parece preexistente.
- Surge un riesgo de seguridad, privacidad, integridad o pérdida de datos.
- Se alcanza el límite de iteraciones.

Al detenerse, el agente conserva el estado, evita acciones de limpieza y reporta evidencia suficiente para decidir el siguiente paso.

## Escalamiento humano obligatorio

Se requiere aprobación humana explícita para:

- Autenticación o autorización.
- Flyway, esquema, migración o transformación de datos.
- Concurrencia, locks, versionado o constraints.
- Secretos, credenciales o variables de entorno.
- Cambios incompatibles de API.
- Dependencias nuevas o actualizadas.
- Acceso de red, servicios externos o producción.
- Desacuerdo entre Planner, Builder, Debugger o Reviewer.
- Cualquier aumento del presupuesto aprobado.

## Handoffs entre agentes

Los handoffs son breves y enlazan evidencia; no copian historiales, archivos completos ni prompts extensos.

### Planner → Builder

- Objetivo y criterios de aceptación.
- Alcance y fuera de alcance.
- Invariantes y riesgos relevantes por referencia.
- Archivos o capas probablemente afectados.
- Presupuesto aprobado y gates aplicables.
- Supuestos o decisiones humanas ya resueltas.

### Builder → Reviewer

- Resumen del cambio y archivos modificados.
- Decisiones técnicas no obvias.
- Pruebas añadidas o actualizadas.
- Gates ejecutados, resultados y omisiones.
- Desviaciones del plan y riesgos residuales.

### Reviewer → Builder

- Findings ordenados por severidad, con ubicación y evidencia.
- Resultado esperado de cada corrección.
- Gates que deben repetirse.
- Veredicto: corregir o escalar.

El Reviewer no prescribe refactors fuera del objetivo ni corrige directamente el trabajo.

### Reviewer → Humano

- Veredicto final: aprobar, solicitar cambios o escalar.
- Findings bloqueantes y no bloqueantes pendientes.
- Estado de gates y validaciones omitidas.
- Cumplimiento del alcance y presupuesto.
- Riesgos residuales y decisiones requeridas.

## Cierre de tarea

Antes de que el humano haga commit o abra un PR, el sistema entrega:

- Un diff limitado al alcance aprobado.
- Lista de archivos modificados y motivo de cada uno.
- Evidencia de criterios de aceptación cumplidos.
- Resumen de pruebas y gates con su resultado real.
- Validaciones omitidas, bloqueos y riesgos residuales.
- Confirmación de que no incluyó secretos, artefactos generados o trabajo ajeno.
- Veredicto del Reviewer final.
- Decisiones pendientes que solo el humano puede tomar.

El humano inspecciona el diff, decide el mensaje de commit, publica la rama y gestiona el PR. Ningún resultado del loop implica integración automática.
