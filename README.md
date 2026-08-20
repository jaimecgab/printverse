# PrintVerse

PrintVerse es el backend de gestión de clientes y cotizaciones para un negocio real de impresión 3D. Esta primera iteración permite mantener catálogos configurables, cotizar piezas con costos técnicos reales y conservar los costos históricos usados en cada propuesta.

## Stack

- Java 21 y Spring Boot 3.5
- Spring Web, Spring Data JPA, Hibernate y Jakarta Validation
- PostgreSQL 17
- Maven, JUnit 5 y Mockito
- Docker y Docker Compose

## Arquitectura

La aplicación es un monolito modular organizado por responsabilidad:

- `controller`: API HTTP y códigos de respuesta.
- `dto`: contratos de entrada y salida; las entidades JPA no salen de los controllers.
- `service`: casos de uso, transiciones de estado y motor de cálculo.
- `domain`: entidades y relaciones persistentes.
- `repository`: acceso a PostgreSQL mediante Spring Data JPA.
- `exception`: errores de negocio y respuestas `ProblemDetail` centralizadas.
- `config`: datos iniciales idempotentes y configuración de aplicación.

## Ejecución

Requisitos locales: JDK 21, Maven 3.9+ y Docker Compose.

Levantar únicamente PostgreSQL:

```bash
docker compose up -d db
mvn spring-boot:run
```

Levantar PostgreSQL y el backend en contenedores:

```bash
docker compose up --build
```

La API queda disponible en `http://localhost:8080`. El volumen `printverse_postgres_data` conserva la información entre reinicios.

Variables configurables y sus valores de desarrollo:

| Variable | Valor por defecto |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/printverse` |
| `DB_USERNAME` | `printverse` |
| `DB_PASSWORD` | `printverse` |
| `JPA_DDL_AUTO` | `update` |
| `SEED_DATA_ENABLED` | `true` |

El seed comprueba el nombre antes de insertar y crea una sola vez `PLA` a 350 MXN/kg y `Creality K1C` a 10 MXN/h. Puede desactivarse con `SEED_DATA_ENABLED=false`.

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

```bash
mvn test
mvn verify
```

Las pruebas cubren el motor monetario, cantidades, precio manual, IVA, descuento, utilidad, margen, snapshots, validaciones y transiciones de estado.

## Alcance actual

Esta iteración no incluye autenticación, frontend React, PDF ni órdenes de producción. Esas capacidades se incorporarán en iteraciones posteriores sobre esta API.
