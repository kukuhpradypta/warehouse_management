# Shop Warehouse Management API

A REST API for managing a shop's warehouse inventory: **items**, their **variants** (size/colour, each with its own SKU, price and stock), and **stock operations** that make overselling impossible.

## Tech Stack

| Concern        | Choice                                        |
|----------------|-----------------------------------------------|
| Language       | Java 17                                       |
| Framework      | Spring Boot 3.5 (Web, Data JPA, Validation)   |
| Build          | Maven (wrapper included)                      |
| Database       | PostgreSQL 14+ (H2 in PostgreSQL mode for tests) |
| Migrations     | Flyway                                        |
| API docs       | springdoc-openapi + Scalar                    |
| Tests          | JUnit 5, Mockito, Spring MockMvc, AssertJ     |

## Architecture

Classic layered architecture, one direction of dependencies:

```text
HTTP ──► controller ──► service ──► repository ──► PostgreSQL
            │              │
           dto          entity
     (request/response)  (JPA)
```

```text
com.shop.warehouse
├── controller/   REST endpoints + GlobalExceptionHandler
├── service/      business rules and transaction boundaries (ItemService, VariantService, StockService)
├── repository/   Spring Data JPA, incl. the atomic stock UPDATE queries
├── entity/       Item, Variant
├── dto/          request records (with Bean Validation) and response records
├── mapper/       entity → response DTO
├── exception/    ApiException hierarchy (status + error code per business failure)
└── config/       OpenAPI, Clock
```

- Controllers only deal with HTTP (status codes, `Location` headers, `@Valid`).
- Services own the business rules and every `@Transactional` boundary.
- `StockService` is the **only** code path that changes stock.

## Domain Model

```text
items                          variants
─────                          ────────
id           PK                id              PK
name         NOT NULL          item_id         FK → items.id (ON DELETE RESTRICT), indexed
description                    sku             UNIQUE, NOT NULL
created_at                     name            NOT NULL
updated_at                     price           NUMERIC(12,2), CHECK (price >= 0)
                               stock_quantity  INTEGER,       CHECK (stock_quantity >= 0)
                               created_at
                               updated_at
Item 1 ────< 0..n Variant
```

**Price and stock live on the Variant only.** You never sell "a T-Shirt"; you sell "T-Shirt Black / M". Putting price/stock on both levels would create two sources of truth that can disagree. An item:

- can exist without variants: it's a catalogue entry that cannot be sold yet;
- without real options is modelled with one default variant (see `Ceramic Mug` → `MUG-STD` in the sample data).

## Business Rules

| Rule | Application | Database |
|------|-------------|----------|
| SKU is unique (case-insensitive, stored trimmed and upper-cased) | `DuplicateSkuException` → 409 `DUPLICATE_SKU` | `uk_variants_sku` |
| SKU format: letters, digits, `-`, `_` | Bean Validation → 400 | - |
| Price ≥ 0, max 2 decimals | Bean Validation → 400 | `ck_variants_price_non_negative` |
| Stock ≥ 0 | Bean Validation on create; guarded UPDATE on sale | `ck_variants_stock_non_negative` |
| Stock quantity per operation is 1 … 1,000,000 | Bean Validation + service guard → 400 | - |
| Cannot sell more than available | 409 `INSUFFICIENT_STOCK`, stock unchanged | CHECK constraint as last line of defence |
| Variant must belong to an existing item | 404 `RESOURCE_NOT_FOUND` | FK `fk_variants_item` |
| A variant is only reachable through its own item | `/items/2/variants/1` → 404 if variant 1 belongs to item 1 | - |
| Item with variants cannot be deleted | 409 `ITEM_HAS_VARIANTS` | `ON DELETE RESTRICT` |
| Stock cannot be set through `PUT` | Not in the DTO; unknown JSON fields → 400 | `stock_quantity` is `updatable = false` in the JPA mapping |

### Stock operations and concurrency

Selling is one conditional SQL statement:

```sql
UPDATE variants
   SET stock_quantity = stock_quantity - :quantity, updated_at = :now
 WHERE id = :variantId AND item_id = :itemId AND stock_quantity >= :quantity
```

- If 1 row is updated, the sale succeeded.
- If 0 rows are updated, the service checks whether the variant exists (404) or the stock was too low (409).

Why this approach over the alternatives:

- **Atomic conditional UPDATE (chosen).** There's no read-modify-write in Java, so there's nothing to race. PostgreSQL row-locks during the UPDATE and re-evaluates the `WHERE` against the latest committed row, so concurrent sales serialise correctly. No retries, and no lock held across round trips.
- **Pessimistic locking (`SELECT … FOR UPDATE`).** It's also correct, but it needs two statements and holds the lock longer for the same result.
- **Optimistic locking (`@Version`).** Under contention on a popular SKU, most requests would fail with a version conflict and need client or server retries.

There's one more detail. `stock_quantity` is mapped `updatable = false`, so a concurrent `PUT` that edits a variant's name or price can never write back a stale stock value and silently undo a sale.

This is verified by `StockApiIntegrationTest.concurrentSalesNeverOversell` (30 threads racing for 10 units). It was also checked manually against PostgreSQL: 60 parallel HTTP sales on 20 units gave 20 × `200`, 40 × `409`, and a final stock of 0.

## Setup

### Prerequisites

- JDK 17 or newer (Maven is not required; use the wrapper)
- PostgreSQL 14+ running locally

### 1. Clone

```bash
git clone https://github.com/kukuhpradypta/warehouse_management.git
cd warehouse_management
```

### 2. Create the database

On your local PostgreSQL server:

```sql
CREATE DATABASE warehouse_db;
```

Flyway builds the tables and loads the sample data automatically on first start.

### 3. Configure environment variables (required)

The database connection is read **only** from environment variables; there are no hard-coded
credentials or database name in `application.properties`. The app refuses to start if any are
missing, so it is always unambiguous which database is in use.

For local development, copy the example file and edit it. The `springboot3-dotenv` dependency
loads `.env` from the project root into Spring automatically, so you do **not** need to `source`
it manually:

```bash
cp .env.example .env   # .env is git-ignored
```

| Variable      | Required | Example                                         |
|---------------|----------|-------------------------------------------------|
| `DB_URL`      | yes      | `jdbc:postgresql://localhost:5432/warehouse_db` |
| `DB_USERNAME` | yes      | `postgres`                                       |
| `DB_PASSWORD` | yes      | `password`                                       |
| `SERVER_PORT` | no       | `8080` (default)                                 |

Real environment variables always take precedence over `.env`, so in production you set these
directly in the environment and don't ship a `.env` file. The example values are throwaway
local-development credentials; no secrets are committed.

### 4. Migrations

Flyway runs automatically on startup:

| Version | Location        | Content |
|---------|-----------------|---------|
| V1      | `db/migration`  | `items` table |
| V2      | `db/migration`  | `variants` table, FK, unique, check constraints, index |
| V3      | `db/seed`       | Sample data (3 items, 8 variants) |

Sample data is kept in a separate location so it can be skipped: `SPRING_FLYWAY_LOCATIONS=classpath:db/migration`.

### 5. Run

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Or build and run the jar:

```bash
./mvnw clean package
java -jar target/warehouse-management-1.0.0.jar
```

- API base URL: `http://localhost:8080/api`
- API reference (Scalar): http://localhost:8080/docs

## Running Tests

```bash
./mvnw test
```

Tests need no running database. They use in-memory H2 in PostgreSQL mode, with the schema built by the **same Flyway migrations** as production. Current suite: 60 tests.

| Test class | Type | Focus |
|------------|------|-------|
| `ItemServiceTest` | Unit (Mockito) | CRUD, delete blocked by variants, not-found |
| `VariantServiceTest` | Unit (Mockito) | SKU normalisation, duplicate SKU, item must exist, update leaves stock alone |
| `StockServiceTest` | Unit (Mockito) | Success, insufficient stock vs not-found, zero/negative quantity |
| `ItemApiIntegrationTest` | `@SpringBootTest` + MockMvc | Endpoints, validation, malformed JSON, unknown fields, 404/405 format |
| `VariantApiIntegrationTest` | `@SpringBootTest` + MockMvc | Duplicate SKU, field errors, variant/item ownership, stock not editable via PUT |
| `StockApiIntegrationTest` | `@SpringBootTest` + MockMvc | 5 → sell 3 → sell 2 → sell 1 rejected, increase, invalid quantities, **concurrency** |

Integration tests commit for real (no rollback-per-test), so constraints and concurrent transactions behave as in production.

## API

All bodies are JSON. Timestamps are ISO-8601 UTC. Prices are in IDR.

| Method | URL | Purpose | Success | Errors |
|--------|-----|---------|---------|--------|
| POST   | `/api/items` | Create item | 201 + `Location` | 400 |
| GET    | `/api/items` | List items (ordered by id) | 200 | |
| GET    | `/api/items/{itemId}` | Get item | 200 | 404 |
| PUT    | `/api/items/{itemId}` | Replace name/description | 200 | 400, 404 |
| DELETE | `/api/items/{itemId}` | Delete item | 204 | 404, 409 `ITEM_HAS_VARIANTS` |
| POST   | `/api/items/{itemId}/variants` | Create variant | 201 + `Location` | 400, 404, 409 `DUPLICATE_SKU` |
| GET    | `/api/items/{itemId}/variants` | List variants of item | 200 | 404 |
| GET    | `/api/items/{itemId}/variants/{variantId}` | Get variant | 200 | 404 |
| PUT    | `/api/items/{itemId}/variants/{variantId}` | Replace SKU/name/price | 200 | 400, 404, 409 `DUPLICATE_SKU` |
| DELETE | `/api/items/{itemId}/variants/{variantId}` | Delete variant | 204 | 404 |
| POST   | `/api/items/{itemId}/variants/{variantId}/stock/increase` | Restock | 200 | 400, 404 |
| POST   | `/api/items/{itemId}/variants/{variantId}/stock/decrease` | Sell | 200 | 400, 404, 409 `INSUFFICIENT_STOCK` |

The examples below use the sample data (item `1` = T-Shirt, variant `2` = `TS-BLK-M` with stock 35, variant `4` = `TS-WHT-M` with stock 0).

### Items

**Create**

```bash
curl -i -X POST http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -d '{"name": "Hoodie", "description": "Fleece hoodie"}'
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/api/items/4
```

```json
{
  "id": 4,
  "name": "Hoodie",
  "description": "Fleece hoodie",
  "createdAt": "2026-10-01T02:18:55.892340Z",
  "updatedAt": "2026-10-01T02:18:55.892340Z"
}
```

**List / get**

```bash
curl http://localhost:8080/api/items
curl http://localhost:8080/api/items/1
```

**Update** (full replacement; omitting `description` clears it)

```bash
curl -X PUT http://localhost:8080/api/items/4 \
  -H "Content-Type: application/json" \
  -d '{"name": "Hoodie Premium", "description": "Heavy fleece"}'
```

**Delete**

```bash
curl -i -X DELETE http://localhost:8080/api/items/4     # 204 No Content
curl -i -X DELETE http://localhost:8080/api/items/1     # 409, item 1 has variants
```

```json
{
  "timestamp": "2026-10-01T02:19:00.477466Z",
  "status": 409,
  "error": "ITEM_HAS_VARIANTS",
  "message": "Item 1 still has variants; delete its variants first",
  "path": "/api/items/1"
}
```

### Variants

**Create** (`stockQuantity` is the initial stock)

```bash
curl -i -X POST http://localhost:8080/api/items/1/variants \
  -H "Content-Type: application/json" \
  -d '{"sku": "TS-WHT-L", "name": "White / L", "price": 149000, "stockQuantity": 10}'
```

```json
{
  "id": 9,
  "itemId": 1,
  "sku": "TS-WHT-L",
  "name": "White / L",
  "price": 149000.00,
  "stockQuantity": 10,
  "createdAt": "2026-10-01T02:18:56.343205Z",
  "updatedAt": "2026-10-01T02:18:56.343205Z"
}
```

**Duplicate SKU** (`ts-blk-m` collides with `TS-BLK-M`):

```bash
curl -X POST http://localhost:8080/api/items/1/variants \
  -H "Content-Type: application/json" \
  -d '{"sku": "ts-blk-m", "name": "Copy", "price": 1, "stockQuantity": 1}'
```

```json
{
  "timestamp": "2026-10-01T02:18:56.709704Z",
  "status": 409,
  "error": "DUPLICATE_SKU",
  "message": "Variant with SKU TS-BLK-M already exists",
  "path": "/api/items/1/variants"
}
```

**List / get**

```bash
curl http://localhost:8080/api/items/1/variants
curl http://localhost:8080/api/items/1/variants/2
```

**Update** (SKU, name and price; stock is not accepted here)

```bash
curl -X PUT http://localhost:8080/api/items/1/variants/2 \
  -H "Content-Type: application/json" \
  -d '{"sku": "TS-BLK-M", "name": "Black / M", "price": 139000}'
```

**Delete**

```bash
curl -i -X DELETE http://localhost:8080/api/items/1/variants/9   # 204 No Content
```

### Stock

**Increase (restock)**

```bash
curl -X POST http://localhost:8080/api/items/1/variants/2/stock/increase \
  -H "Content-Type: application/json" \
  -d '{"quantity": 5}'
```

Response `200` is the updated variant, e.g. `"stockQuantity": 40`.

**Decrease (sell)**

```bash
curl -X POST http://localhost:8080/api/items/1/variants/2/stock/decrease \
  -H "Content-Type: application/json" \
  -d '{"quantity": 3}'
```

Response `200` is the updated variant with the reduced `stockQuantity`.

**Insufficient stock** (variant 4 has stock 0, which stays 0):

```bash
curl -X POST http://localhost:8080/api/items/1/variants/4/stock/decrease \
  -H "Content-Type: application/json" \
  -d '{"quantity": 1}'
```

```json
{
  "timestamp": "2026-10-01T02:18:58.439132Z",
  "status": 409,
  "error": "INSUFFICIENT_STOCK",
  "message": "Insufficient stock for variant TS-WHT-M: requested 1, available 0",
  "path": "/api/items/1/variants/4/stock/decrease"
}
```

### Error format

Every error uses the same shape. `errors` only appears for field validation failures.

```bash
curl -X POST http://localhost:8080/api/items \
  -H "Content-Type: application/json" \
  -d '{"name": ""}'
```

```json
{
  "timestamp": "2026-10-01T02:18:57.132537Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/items",
  "errors": {
    "name": "Name must not be blank"
  }
}
```

| `error` | Status | When |
|---------|--------|------|
| `VALIDATION_ERROR` | 400 | Bean Validation failed (`errors` lists each field) |
| `MALFORMED_REQUEST` | 400 | Invalid JSON, wrong type, or unknown field (e.g. sending `id` or `stockQuantity`) |
| `INVALID_STOCK_QUANTITY` | 400 | Non-positive quantity reaching the service |
| `RESOURCE_NOT_FOUND` | 404 | Item/variant doesn't exist, or the variant belongs to another item |
| `DUPLICATE_SKU` | 409 | SKU already used |
| `INSUFFICIENT_STOCK` | 409 | Sale exceeds available stock |
| `ITEM_HAS_VARIANTS` | 409 | Deleting an item that still has variants |
| `DATA_INTEGRITY_VIOLATION` | 409 | Any other database constraint (no DB details exposed) |
| `NOT_FOUND`, `METHOD_NOT_ALLOWED`, … | 4xx | Spring MVC errors (unknown route, wrong method, …) in the same format |
| `INTERNAL_ERROR` | 500 | Unexpected failure; details are logged, not returned |

## Design Decisions

- **Variant-level price and stock.** This gives one source of truth for what is actually sold. Single-option products get a default variant instead of special-case code.
- **DTOs (Java records) instead of entities.** The API contract is decoupled from the schema. Clients cannot set `id`, `createdAt`, `updatedAt` or `stockQuantity`, and they get an explicit 400 if they try, because `fail-on-unknown-properties` is enabled so mistakes aren't silently ignored. Separate create/update DTOs encode different rules: initial stock is accepted on create only.
- **Service layer.** Business rules and transaction boundaries live in one testable place. Controllers stay thin.
- **Separate `StockService` with action endpoints.** `POST …/stock/decrease {"quantity": 3}` expresses intent. A `PUT stockQuantity=7` would let concurrent clients overwrite each other.
- **PostgreSQL.** It's a relational model with real FK/unique/CHECK constraints and well-defined row locking under `READ COMMITTED`, which the stock strategy relies on.
- **Flyway; Hibernate set to `validate` only.** The schema is versioned, reviewable SQL. Hibernate fails fast if the mapping drifts from it.
- **Constraints in the database as well as in Java.** Validation gives good error messages, and constraints guarantee integrity even under races or direct SQL.
- **Restrict, not cascade, on item delete.** Cascading would silently delete variants and their stock records. Making it explicit is safer for inventory data.
- **Centralised error handling.** `ApiException` subclasses carry status and error code. `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler`, so framework errors (404 route, 405, 415, type mismatch) use the same JSON shape.
- **No Lombok.** Records cover DTOs. Entities have a few explicit getters and intention-revealing methods (`updateDetails`) instead of public setters.
- **`Instant` / `TIMESTAMP WITH TIME ZONE`.** Timestamps are unambiguous regardless of server time zone.

## Assumptions

1. **Currency is IDR.** Prices are stored as `NUMERIC(12,2)`; 2 decimals are allowed even though IDR is usually whole.
2. **Single warehouse.** There's one stock level per variant.
3. **SKUs are case-insensitive.** They're normalised to trimmed upper-case and must be globally unique, not just per item.
4. **Item names are not unique.** The SKU is the business identifier.
5. **"Sell" means an immediate stock decrement.** There are no reservations, orders or payments.
6. **Deletes are hard deletes.** Deleting a variant is allowed regardless of its stock. Deleting an item requires removing its variants first.
7. **`PUT` is full replacement.** All editable fields are required, except `description`, which can be cleared.
8. **Concurrent catalogue edits are last-write-wins.** Two admins editing the same variant's name/price at once is low-risk; stock is fully protected regardless.
9. **No authentication**, as it's out of scope for the assessment. **The API is open to anyone who can reach it**, so don't expose it publicly as-is.
10. **Lists are not paginated.** The data volume is assessment-sized.

## Known Limitations

- Tests run against H2 (PostgreSQL mode), not PostgreSQL itself. The production behaviour was additionally verified manually against PostgreSQL 14, but there's no automated PostgreSQL test (e.g. Testcontainers).
- Spring Boot 3.5 is the last 3.x line, which the assessment requires. Its open-source support has ended, so a real project should plan the move to Boot 4.
- The API reference (Scalar) at `/docs` is enabled in all environments. Disable it with `scalar.enabled=false` (and `springdoc.api-docs.enabled=false` to also drop the underlying OpenAPI document) where it isn't wanted.

## Future Improvements

- Authentication and authorisation (e.g. Spring Security + JWT; only staff can restock)
- Pagination, sorting and filtering on list endpoints
- Inventory movement history (an append-only `stock_movements` table: who, when, why, delta) for audit and reporting
- Optimistic locking (`@Version` + `If-Match`/ETag) for catalogue edits
- Idempotency keys on stock operations so client retries can't double-sell
- Testcontainers-based PostgreSQL integration tests
- Observability: Spring Boot Actuator health/metrics, structured logging
