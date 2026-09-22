# AGENTS.md

## Propósito

PrintVerse gestiona clientes, catálogos, cotizaciones y producción para un taller de impresión 3D.
Es un monolito modular con una API Spring Boot, una SPA React y PostgreSQL.
El backend es la autoridad sobre cálculos, permisos, transiciones y persistencia.
Consulta `README.md` para arquitectura, flujos, fórmulas y operación detallada; no los dupliques aquí.

## Mapa mínimo

| Ruta | Responsabilidad |
| --- | --- |
| `src/main/java/com/printverse/config/` | Seguridad, CORS, OpenAPI y bootstrap |
| `src/main/java/com/printverse/controller/` | API REST y DTOs en los bordes |
| `src/main/java/com/printverse/service/` | Casos de uso, cálculos y transacciones |
| `src/main/java/com/printverse/domain/` | Entidades y estados del dominio |
| `src/main/java/com/printverse/repository/` | Persistencia JPA y locks |
| `src/main/resources/db/migration/` | Migraciones Flyway |
| `src/test/` | Pruebas backend, integración y concurrencia |
| `frontend/src/` | SPA, cliente HTTP, tipos y pruebas frontend |
| `.github/workflows/ci.yml` | Gates vigentes de CI |
| `docker-compose.yml` | Stack integrado local |

## Invariantes críticas

1. Spring Boot decide importes finales, permisos y transiciones; la SPA no sustituye esas reglas.
2. Dinero y porcentajes backend usan `BigDecimal`, escalas y redondeo `HALF_UP` existentes.
3. Solo una cotización `DRAFT` puede modificarse.
4. Los snapshots históricos de cliente, material, impresora y costos no se recalculan desde catálogos actuales.
5. Convertir una cotización aceptada en producción debe seguir siendo idempotente.
6. Una impresora solo está ocupada por una pieza `IN_PROGRESS`; trabajos en cola no la reservan.
7. No relajes locks pesimistas, `@Version`, orden de locks ni constraints de ocupación sin aprobación explícita.
8. Las migraciones Flyway ya aplicadas son inmutables; añade una nueva migración para evolucionar el esquema.
9. Hibernate debe conservar `ddl-auto=validate`; nunca usar actualización automática del esquema.
10. Los controllers intercambian DTOs y no exponen entidades JPA.
11. La autorización debe aplicarse en backend aunque la UI oculte o deshabilite acciones.

## Seguridad y operación

- No leas, muestres, copies ni registres `.env`, secretos, JWT, contraseñas, claves, dumps o credenciales.
- Los archivos `.env.example` son plantillas públicas; no asumas que sus valores sirven para producción.
- No introduzcas secretos en código, prompts, pruebas, logs, documentación o commits.
- No accedas a producción ni ejecutes despliegues.
- No borres datos, volúmenes, ramas, worktrees ni archivos ajenos.
- No ejecutes migraciones destructivas ni comandos privilegiados sin aprobación humana explícita.
- No instales ni actualices dependencias sin autorización.
- No reduzcas autenticación, autorización, validación, locks o tests para conseguir un resultado verde.
- Cambios de seguridad, concurrencia, contratos públicos o datos requieren revisión humana.

## Política Git

- `main` es la rama canónica; las tareas parten de una base aprobada y actualizada.
- Todo agente escritor trabaja en una rama y un worktree dedicados, preparados por una persona autorizada.
- Solo un agente escritor puede usar cada worktree; planner y reviewer permanecen en lectura.
- Antes de editar, confirma raíz, rama y `git status --short --branch`.
- Si aparecen cambios o archivos inesperados, detente y pide instrucciones; no los leas ni manipules por defecto.
- No cambies de rama y no uses `stash` ni auto-stash.
- No ejecutes `commit`, `push`, `merge`, `rebase`, `reset`, `clean` ni force-push.
- No reviertas, sobrescribas ni descartes trabajo que no hayas creado.
- La persona responsable decide commits, publicación, integración y limpieza del worktree.

## Matriz compacta de gates

Los gates son acumulativos: aplica todas las filas afectadas por el cambio.

| Tipo de cambio | Validación mínima obligatoria |
| --- | --- |
| Documentación o gobernanza | `git diff --check`; revisar enlaces, rutas y comandos |
| Frontend | En `frontend/`: `npm run test`, `npm run lint`, `npm run build` |
| Backend Java | `mvn --batch-mode verify` |
| API o DTO | Gates backend y frontend; revisar OpenAPI y tipos TypeScript |
| Base de datos o Flyway | Backend con Docker; pruebas de base fresca y baseline; revisar migraciones |
| Docker, Compose o Nginx | `docker compose config --quiet`, build y smoke test del stack |
| Seguridad o autorización | Gates de capas afectadas, pruebas de permisos y revisión humana |
| Concurrencia o producción | Backend con Docker, pruebas concurrentes relevantes y revisión humana |
| Dependencias | Gates completos del área y revisión explícita de manifiesto y lockfile |

- Ejecuta primero la prueba específica y después el gate completo aplicable.
- `npm ci` prepara el entorno: no lo ejecutes sin autorización y no lo presentes como gate.
- Para Flyway o concurrencia, un build que omita Testcontainers no cuenta como validación completa.
- Si un gate no puede ejecutarse, informa el comando, motivo y riesgo; nunca lo declares exitoso.
- No corrijas fallos preexistentes o no relacionados sin ampliar explícitamente el alcance.

## Regla de alcance

- Resuelve un solo objetivo aprobado con el cambio mínimo coherente.
- Inspecciona antes de editar y sigue estructura, nombres y estilo existentes.
- No hagas refactors oportunistas, reformateos masivos ni limpiezas no solicitadas.
- No añadas dependencias, variables de entorno, migraciones, endpoints o cambios públicos de API fuera del plan.
- Actualiza pruebas cuando cambie comportamiento; no cambies expectativas solo para ocultar una regresión.
- No edites artefactos generados como `target/`, `frontend/dist/`, cobertura o cachés.
- Si necesitas tocar capas o archivos no previstos, detente y solicita aprobación antes de continuar.
- Distingue hechos verificados, inferencias y aspectos que no pudiste comprobar.

## Flujo de trabajo mínimo

1. Verifica instrucciones, estado Git, rama, worktree y criterios de aceptación.
2. Localiza la implementación y las pruebas existentes antes de proponer cambios.
3. Identifica invariantes, capas afectadas, riesgos y gates aplicables.
4. Expón cualquier supuesto que no pueda comprobarse desde el repositorio.
5. Implementa solo el alcance aprobado y conserva los contratos existentes.
6. Añade o ajusta la prueba más cercana al comportamiento modificado.
7. Ejecuta gates desde el más específico y barato hasta el más amplio.
8. Revisa el diff completo, no solo los archivos editados recientemente.
9. Detente si surge una decisión de producto, seguridad o datos no aprobada.
10. Entrega un resumen verificable sin ocultar fallos, skips ni validaciones pendientes.

## Definition of Done

Una tarea está terminada únicamente cuando:

- Cumple los criterios de aceptación y respeta todas las invariantes aplicables.
- El diff contiene solo archivos dentro del alcance aprobado.
- Las pruebas necesarias fueron añadidas o actualizadas cuando cambió comportamiento.
- Todos los gates aplicables pasaron, o las omisiones están declaradas con su riesgo.
- `git diff --check` pasa.
- No hay secretos, artefactos generados ni cambios ajenos incluidos.
- Se revisó el diff completo para detectar regresiones, seguridad, concurrencia y compatibilidad.
- El reporte final enumera archivos cambiados, validaciones ejecutadas y riesgos residuales.
- No se realizaron commits, push, merge, deploy ni otras acciones reservadas a una persona.
