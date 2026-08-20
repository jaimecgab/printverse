# PrintVerse

PrintVerse es una aplicación web para gestionar clientes, catálogos de costos y cotizaciones de un negocio real de impresión 3D. En la Iteración 2, una SPA en React permite operar el flujo completo de cotización sobre la API Spring Boot: preparar catálogos, crear borradores progresivamente, calcular piezas con costos técnicos reales y consultar las condiciones históricas de cada propuesta.

## Stack

- Java 21 y Spring Boot 3.5
- Spring Web, Spring Data JPA, Hibernate y Jakarta Validation
- PostgreSQL 17
- React 19, TypeScript 5.7 y Vite 6
- React Router 7, Lucide React y CSS
- Maven, JUnit 5, Mockito, npm y ESLint
- Docker y Docker Compose

## Arquitectura

La aplicación separa una SPA cliente de una API y una base de datos:

- `frontend/`: aplicación React TypeScript construida con Vite. Define las pantallas, navegación, formularios y estados de carga/error, y consume JSON mediante `fetch`.
- `frontend/src/api`: cliente HTTP tipado y adaptadores para clientes, materiales, impresoras y cotizaciones.
- `frontend/src/pages`: dashboard, catálogos y flujo de cotizaciones. React Router resuelve las rutas en el navegador.
- `src/main/java`: monolito modular Spring Boot. El backend valida las reglas de negocio, es la fuente autoritativa de los cálculos y persiste en PostgreSQL.

Dentro del backend, los paquetes se organizan por responsabilidad:

- `controller`: API HTTP y códigos de respuesta.
- `dto`: contratos de entrada y salida; las entidades JPA no salen de los controllers.
- `service`: casos de uso, transiciones de estado y motor de cálculo.
- `domain`: entidades y relaciones persistentes.
- `repository`: acceso a PostgreSQL mediante Spring Data JPA.
- `exception`: errores de negocio y respuestas `ProblemDetail` centralizadas.
- `config`: CORS, datos iniciales idempotentes y configuración de aplicación.

## Ejecución

Requisitos locales: JDK 21, Maven 3.9+, Node.js con npm y Docker Compose.

Desde la raíz del repositorio, inicia PostgreSQL:

```bash
docker compose up -d db
```

Inicia el backend en otra terminal, también desde la raíz:

```bash
mvn spring-boot:run
```

Inicia el frontend en una tercera terminal:

```bash
cd frontend
npm install
npm run dev
```

Abre `http://localhost:5173` en el navegador. La SPA consume la API disponible en `http://localhost:8080`; el volumen `printverse_postgres_data` conserva la información entre reinicios.

Variables configurables y sus valores de desarrollo:

| Variable | Valor por defecto |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/printverse` |
| `DB_USERNAME` | `printverse` |
| `DB_PASSWORD` | `printverse` |
| `JPA_DDL_AUTO` | `update` |
| `SEED_DATA_ENABLED` | `true` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |
| `VITE_API_BASE_URL` | `http://localhost:8080` |

El seed comprueba el nombre antes de insertar y crea una sola vez `PLA` a 350 MXN/kg y `Creality K1C` a 10 MXN/h. Puede desactivarse con `SEED_DATA_ENABLED=false`.

`VITE_API_BASE_URL` es una variable del frontend y puede definirse en `frontend/.env`; si no existe, el cliente usa `http://localhost:8080`. `CORS_ALLOWED_ORIGINS` configura en el backend los orígenes autorizados para `/api/**` y por defecto permite `http://localhost:5173`.

## Flujo de cotización

La creación es progresiva para evitar un formulario monolítico:

1. Se registran un cliente y, para agregar piezas, al menos un material y una impresora activos.
2. En `/quotes/new` se eligen el cliente, vigencia, entrega, anticipo, markup, descuento, IVA y notas. Al guardar, la API crea una cotización `DRAFT` aunque todavía no tenga piezas.
3. La SPA redirige al editor del borrador. Allí se agregan una o más piezas con material, impresora, peso, tiempo, riesgo de falla, precio manual opcional y cargos adicionales por unidad.
4. Cada mutación se envía a la API; el backend recalcula y devuelve el resumen financiero autoritativo que presenta la interfaz.
5. La vista final permite marcar el borrador como `SENT` y después registrar `ACCEPTED` o `REJECTED`. Una cotización deja de ser editable al enviarse.

Cada pieza guarda instantáneas de `pricePerKg` y `costPerHour` al crearse. Los cambios posteriores en los catálogos no alteran cotizaciones históricas y la acción de recalcular conserva esas instantáneas. Cambiar explícitamente el material o la impresora de una pieza mientras la cotización está en `DRAFT` toma una instantánea nueva.

## API REST

### Clientes y catálogos

| Método | Ruta | Operación |
| --- | --- | --- |
| `POST` | `/api/customers` | Crear cliente |
| `GET` | `/api/customers` | Listar clientes |
| `GET` | `/api/customers/{id}` | Consultar cliente |
| `PUT` | `/api/customers/{id}` | Actualizar cliente |
| `POST` | `/api/materials` | Crear material |
| `GET` | `/api/materials` | Listar materiales |
| `GET` | `/api/materials/{id}` | Consultar material |
| `PUT` | `/api/materials/{id}` | Actualizar material y precio |
| `PATCH` | `/api/materials/{id}/active` | Activar o desactivar material |
| `POST` | `/api/printers` | Crear impresora |
| `GET` | `/api/printers` | Listar impresoras |
| `GET` | `/api/printers/{id}` | Consultar impresora |
| `PUT` | `/api/printers/{id}` | Actualizar impresora y costo/hora |
| `PATCH` | `/api/printers/{id}/active` | Activar o desactivar impresora |

### Cotizaciones

| Método | Ruta | Operación |
| --- | --- | --- |
| `POST` | `/api/quotes` | Crear cotización `DRAFT` |
| `GET` | `/api/quotes` | Listar resúmenes |
| `GET` | `/api/quotes/{id}` | Obtener detalle y totales |
| `PUT` | `/api/quotes/{id}` | Editar condiciones, markup, descuento e IVA |
| `POST` | `/api/quotes/{id}/items` | Agregar ítem |
| `PUT` | `/api/quotes/{id}/items/{itemId}` | Editar ítem o precio manual |
| `DELETE` | `/api/quotes/{id}/items/{itemId}` | Eliminar ítem |
| `POST` | `/api/quotes/{id}/items/{itemId}/charges` | Agregar cargo libre por unidad |
| `DELETE` | `/api/quotes/{id}/items/{itemId}/charges/{chargeId}` | Eliminar cargo |
| `POST` | `/api/quotes/{id}/recalculate` | Recalcular usando snapshots guardados |
| `PATCH` | `/api/quotes/{id}/status` | Cambiar estado con reglas de transición |

Solo una cotización `DRAFT` es editable. Las transiciones permitidas son `DRAFT -> SENT` y `SENT -> ACCEPTED|REJECTED`; para enviarla debe contener al menos un ítem.

### Ejemplos

Crear un cliente:

```bash
curl -X POST http://localhost:8080/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ana Torres","phone":"5512345678","email":"ana@example.com","notes":"Contacto por WhatsApp"}'
```

Crear una cotización con 40% de markup e IVA de 16%:

```bash
curl -X POST http://localhost:8080/api/quotes \
  -H 'Content-Type: application/json' \
  -d '{"customerId":1,"validUntil":"2026-09-15","markupPercentage":40,"discountPercentage":0,"taxEnabled":true,"taxPercentage":16}'
```

Agregar una pieza usando el material y la impresora con id 1:

```bash
curl -X POST http://localhost:8080/api/quotes/1/items \
  -H 'Content-Type: application/json' \
  -d '{"name":"Soporte de cámara","quantity":2,"materialId":1,"printerId":1,"weightGrams":120,"printTimeMinutes":180,"failureRiskPercentage":10}'
```

## Motor de cotización

Todos los importes usan `BigDecimal` y redondeo `HALF_UP`. Cada componente monetario y total persistido se redondea a dos decimales; el margen se conserva con cuatro.

Por unidad:

```text
materialCostUnit = (weightGrams / 1000) * materialPricePerKgSnapshot
machineCostUnit = (printTimeMinutes / 60) * printerCostPerHourSnapshot
technicalBaseCostUnit = materialCostUnit + machineCostUnit
failureRiskCostUnit = technicalBaseCostUnit * failureRiskPercentage / 100
additionalChargesUnit = sum(additional charges)
internalCostUnit = technicalBaseCostUnit + failureRiskCostUnit + additionalChargesUnit
suggestedPriceUnit = internalCostUnit * (1 + markupPercentage / 100)
finalUnitPrice = manualUnitPrice ?? suggestedPriceUnit
itemSubtotal = finalUnitPrice * quantity
```

Para toda la cotización:

```text
internalCost = sum(internalCostUnit * quantity)
suggestedSubtotal = sum(suggestedPriceUnit * quantity)
finalSubtotal = sum(finalUnitPrice * quantity)
discountAmount = finalSubtotal * discountPercentage / 100
subtotalAfterDiscount = finalSubtotal - discountAmount
taxAmount = taxEnabled ? subtotalAfterDiscount * taxPercentage / 100 : 0
total = subtotalAfterDiscount + taxAmount
estimatedProfit = subtotalAfterDiscount - internalCost
realMarginPercentage = subtotalAfterDiscount > 0
    ? estimatedProfit / subtotalAfterDiscount * 100
    : 0
```

Al crear un ítem se copian `pricePerKg` y `costPerHour` a snapshots propios. Cambiar después el catálogo no modifica esos valores ni las cotizaciones históricas. Cambiar explícitamente el material o la impresora de un ítem `DRAFT` sí toma un snapshot nuevo.

## Pruebas

Frontend:

```bash
cd frontend
npm run build
npm run lint
```

Backend:

```bash
mvn test
mvn verify
```

Las pruebas cubren el motor monetario, cantidades, precio manual, IVA, descuento, utilidad, margen, snapshots, validaciones y transiciones de estado.

## Alcance actual

La Iteración 2 incluye una SPA responsive con dashboard, gestión de clientes, materiales e impresoras, búsqueda y filtrado de cotizaciones, creación progresiva de borradores, edición de piezas y cargos, desglose financiero, recálculo y transiciones de estado.

Quedan reservados explícitamente para la Iteración 3:

- Autenticación y autorización con JWT.
- Generación y descarga de un PDF real; el control actual es únicamente un marcador deshabilitado.
- La entidad `ProductionOrder` y los flujos de producción derivados de cotizaciones aceptadas.
