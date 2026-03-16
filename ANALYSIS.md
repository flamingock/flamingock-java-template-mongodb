# Flamingock MongoDB Template — Comprehensive Module Analysis

**Module:** `flamingock-java-template-mongodb` v1.2.0-SNAPSHOT
**Flamingock Core:** v1.2.0-beta.1
**Java Target:** 8
**MongoDB Driver:** 4.0.0 (compileOnly)
**Last Updated:** 2026-03-15

---

## 1. Architecture Overview

**Module coordinates:** `io.flamingock:flamingock-java-template-mongodb:1.2.0-SNAPSHOT`

**Execution flow:**
```
YAML
  → MongoOperation (deserialized by framework via YAML mapper)
  → MongoOperation.validate()  [load time — TemplatePayload contract]
      → TypeValidator
      → CollectionValidator
      → OperationValidator (per-type)
  → MongoOperationType.findByTypeOrThrow()  [enum factory]
  → MongoOperator subclass.apply(clientSession)
      → applyInternal(clientSession)
      → MongoDB Driver API
```

Structural validation runs **at load time** — before any change executes — via `MongoOperation.validate()`. The framework calls `validate()` on both apply and rollback payloads during the loaded-change validation phase. A malformed YAML at step 50 is caught before steps 1–49 execute.

**Key classes (37 production source files):**

| Class | Role |
|---|---|
| `MongoChangeTemplate` | Entry point. `@ChangeTemplate(multiStep = true)`. Extends `AbstractChangeTemplate<TemplateVoid, MongoOperation, MongoOperation>`. |
| `MongoOperation` | YAML payload POJO (type, collection, parameters). Implements `TemplatePayload.validate()`. |
| `MongoOperationType` | Enum with 11 operations, factory via `BiFunction`, validator binding. |
| `MongoOperator` | Abstract base. Template method: `apply()` → `logOperation()` → `applyInternal()` + exception wrapping. |
| `MongoTemplateExecutionException` | Contextual wrapper for driver exceptions (type + collection + cause). |
| `DatabaseInspector` | Package-private DDL idempotency checks (collectionExists, indexExistsByName, indexExistsByKeys). |
| `OperationValidator` | Functional interface. Static helpers: `checkUnrecognizedKeys`, `checkUnrecognizedOptionKeys`, `checkListElementTypes`. |
| `TypeValidator` | Validates type is non-null, non-empty, known. |
| `CollectionValidator` | Validates collection name (null, blank, `$`, `\0`). |
| `*ParametersValidator` (8 classes) | One per operation with parameters. |
| `*OptionsMapper` (6 classes) | Converts `Map<String, Object>` options to MongoDB driver option objects. |
| `BsonConverter` | Recursive YAML-map to BsonDocument conversion. |
| `MapperUtil` | Type-safe extraction utilities for option maps. |

**Processing pipeline diagram:**
```
┌─────────────────────────────────────────────────────────────────────────┐
│ YAML Change Step                                                         │
│   apply:                         rollback:                              │
│     type: insert                   type: delete                         │
│     collection: orders             collection: orders                   │
│     parameters: ...                parameters: ...                      │
└─────────┬───────────────────────────────────────┬───────────────────────┘
          │ deserialize                             │ deserialize
          ▼                                         ▼
   MongoOperation                           MongoOperation
          │                                         │
          └──────────── validate() ─────────────────┘
                            │ [LOAD TIME — TypeValidator, CollectionValidator,
                            │              OperationValidator chain]
                            ▼
                    MongoChangeTemplate
                      apply() / rollback()
                            │ validateSession()
                            ▼
                MongoOperationType.findByTypeOrThrow()
                            │
                            ▼
                    MongoOperator subclass
                      apply(clientSession)
                         logOperation()
                         applyInternal(clientSession)
                            │
                            ▼
                    MongoDB Driver API
```

---

## 2. Top 10 Issues (Ranked by Severity)

### Issue 1 — HIGH
**Validation-mapper gap: three index options accepted by validator but throw at execution**

- **File:** `IndexOptionsMapper.java:94-110`, `CreateIndexParametersValidator.java:69-70`
- **Impact:** `bucketSize`, `wildcardProjection`, and `hidden` are present in `IndexOptionsMapper.RECOGNIZED_KEYS` (line 43-44), so `CreateIndexParametersValidator` delegates to them and treats these keys as valid. Load-time validation passes. At execution, `mapToIndexOptions()` throws `UnsupportedOperationException` with a message about the driver version. If `createIndex` is not the first step in a multi-step change, prior non-transactional steps (e.g., `createCollection`) have already executed and cannot be rolled back.
- **Risk:** User writes a valid-looking YAML change that passes startup validation, only to fail mid-execution with a confusing `UnsupportedOperationException` (not a `MongoTemplateExecutionException`). The audit log records the change as started but not completed.
- **Fix:** Remove `bucketSize`, `wildcardProjection`, and `hidden` from `RECOGNIZED_KEYS` and add explicit validation entries in `CreateIndexParametersValidator` that reject them with a clear message: `"'bucketSize' is not supported — removed in MongoDB driver 4.4.0"`. Alternatively, keep in `RECOGNIZED_KEYS` but add an explicit check in the validator for these three keys.

---

### Issue 2 — MEDIUM
**Raw casts in `buildCollationFromMap()` without type-checking produce `ClassCastException` instead of clear validation errors**

- **File:** `MapperUtil.java:108-129`
- **Impact:** `buildCollationFromMap()` uses direct casts: `(Boolean) map.get("caseLevel")`, `(String) map.get("caseFirst")`, `(String) map.get("alternate")`, etc. If a YAML collation block has wrong types (e.g., `caseLevel: "yes"` instead of `true`), these throw `ClassCastException` at execution time, not `IllegalArgumentException`, and without the user-friendly `field[x] should be Y` message from `MapperUtil`'s own helper methods.
- **Risk:** User gets an unintelligible `ClassCastException` stack trace rather than a structured error. There is no validator for collation sub-fields.
- **Fix:** Replace raw casts with the existing `getBoolean()` / `getString()` helper methods from `MapperUtil`, e.g., `builder.caseLevel(getBoolean(map, "caseLevel"))`. This provides consistent error messages and uses the same type-checking pattern already established in the codebase.

---

### Issue 3 — MEDIUM
**Explicit null values in YAML option maps are not validated and cause `IllegalArgumentException` at mapper time**

- **File:** `MapperUtil.java:31-85`, `IndexOptionsMapper.java:49-113`
- **Impact:** All `MapperUtil.get*()` helpers check `containsKey()` before being called (correct), but `Map.containsKey()` returns `true` for keys mapped to `null` in Java. If a user writes `expireAfterSeconds:` (bare key, no value) in YAML, the YAML parser produces `{expireAfterSeconds: null}`. `containsKey("expireAfterSeconds")` is true, so `getLong(options, "expireAfterSeconds")` is called, which hits `value instanceof Number` → false for null → throws `IllegalArgumentException("field[expireAfterSeconds] should be Long")`. This fails at execution time, not load time. Same applies to all other option keys.
- **Risk:** User sees a runtime IAE rather than a load-time validation error. The change starts executing before the error is surfaced, which is the exact failure mode the `TemplatePayload` contract is designed to prevent.
- **Fix:** Extend the option-map validators (e.g., `CreateIndexParametersValidator`) to explicitly reject null values for known keys, producing a structured `TemplatePayloadValidationError` at load time: `"'expireAfterSeconds' cannot be null"`.

---

### Issue 4 — MEDIUM
**`CollectionValidator` only checks two invalid characters; MongoDB's full namespace rules are broader**

- **File:** `CollectionValidator.java:27-45`, `CreateViewParametersValidator.java:57-64`, `RenameCollectionParametersValidator.java` (target name validation)
- **Impact:** Validated characters: `$` and `\0`. Unvalidated: namespace length > 255 bytes, names starting with `system.`, names containing `.` (creates sub-collection ambiguity in some drivers). These will fail at the MongoDB layer with a driver exception rather than a structured Flamingock validation error.
- **Risk:** User gets a raw `MongoCommandException` or driver error at execution time instead of a clear validation message.
- **Fix:** Add checks for the additional constraints from the MongoDB documentation: `system.` prefix rejection and the 255-byte namespace limit. Optionally document which constraints are intentionally delegated to the driver.

---

### Issue 5 — MEDIUM
**Unchecked casts in `MongoOperation` helpers (`getKeys`, `getFilter`, `getOptions`, `isMulti`) create `ClassCastException` risk**

- **File:** `MongoOperation.java:53-73`
- **Impact:** `getKeys()` (line 54-56), `getFilter()` (line 66-68), `getOptions()` (line 59-63), and `isMulti()` (line 70-73) all cast the raw YAML value directly without type verification. These are suppressed with `@SuppressWarnings("unchecked")`. The safety contract is that the corresponding `OperationValidator` runs first and verifies types. This contract is implicit and undocumented in the helper methods themselves.
- **Risk:** If validators are bypassed (e.g., in tests constructing `MongoOperation` directly), or a future refactor moves validator placement, these helpers throw `ClassCastException` with no context.
- **Fix:** Add an assertion or `instanceof` guard with a clear `IllegalStateException("getKeys() called on operation where 'keys' is not a Map — validate() must run first")` to make the precondition explicit. This costs nothing at runtime when validators run correctly and makes bugs self-evident when they don't.

---

### Issue 6 — LOW
**`RenameCollectionOperator` has a gap when both source and target are absent**

- **File:** `RenameCollectionOperator.java:37-44`
- **Impact:** The idempotency check is: `!sourceExists && targetExists → already done, skip`. But `!sourceExists && !targetExists` falls through to call `mongoDatabase.getCollection(source).renameCollection(target, options)`, which throws `MongoCommandException: "the collection does not exist"`. This exception is wrapped in `MongoTemplateExecutionException` but the message is not user-friendly.
- **Risk:** If the source collection was dropped externally (data inconsistency), the rename step fails with a confusing exception instead of a clear "source collection was not found" error.
- **Fix:** Add the missing case:
  ```java
  if (!sourceExists && !targetExists) {
      logger.warn("Neither source '{}' nor target '{}' collection exists — skipping renameCollection", op.getCollection(), targetName);
      return;
  }
  ```
  Or throw `MongoTemplateExecutionException` with an explicit message.

---

### Issue 7 — LOW
**`CreateViewOperator.getPipeline()` returns null for a required-validated parameter**

- **File:** `CreateViewOperator.java:50-55`
- **Impact:** `getPipeline()` returns null when `parameters.get("pipeline") == null`. It then passes null to `mongoDatabase.createView(collection, viewOn, null, options)`, which would throw `NullPointerException` in the MongoDB driver. In practice this is protected by `CreateViewParametersValidator`, which requires `pipeline`. But the method's defensive return-null pattern contradicts the "precondition guaranteed by validator" contract established by comments in `MongoChangeTemplate`.
- **Risk:** Low in normal operation. A test constructing `MongoOperation` without validation could NPE without a clear error.
- **Fix:** Replace `return null` with `throw new IllegalStateException("pipeline is null — validate() guarantees it is non-null")` to make the precondition explicit. Or change to `return Collections.emptyList()` (empty pipeline = identity view), which may be more useful but changes semantics.

---

### Issue 8 — LOW
**`background` index option is deprecated since MongoDB 4.2 but accepted without warning**

- **File:** `IndexOptionsMapper.java:52-54`, `IndexOptionsMapper.RECOGNIZED_KEYS:40`
- **Impact:** MongoDB deprecated background index builds in 4.2 and removed support in later versions. The mapper silently accepts and passes the `background` flag to `IndexOptions.background()`. Users running against MongoDB 5.0+ will find the flag has no effect.
- **Risk:** Misleading behavior — the template accepts the option, no error is thrown, but nothing happens. The user assumes their index was built as background.
- **Fix:** Log a deprecation warning when `background` key is present: `logger.warn("'background' index option is deprecated since MongoDB 4.2 and ignored in 5.0+")`.

---

### Issue 9 — LOW
**`MongoOperationType` type lookup uses linear scan on every operation dispatch**

- **File:** `MongoOperationType.java:75-86`
- **Impact:** `findByTypeOrThrow()` and `findByType()` iterate all 11 enum values via `Arrays.stream()` on every call. With 11 elements the cost is negligible in practice, but the pattern does not scale if operations are added and is called once per step execution.
- **Risk:** Zero at current scale. Technical debt.
- **Fix:** Add a static `Map<String, MongoOperationType>` lookup table initialized in a static block. This is a standard pattern for enum value-to-constant lookups and makes the intent explicit.

---

### Issue 10 — LOW
**`CreateCollection`, `DropCollection`, and `DropView` silently ignore their `options` parameter entirely without validation**

- **File:** `NoParametersValidator.java:38-48`, `CreateCollectionOperator.java:29-35`
- **Impact:** `createCollection` uses `NoParametersValidator` which rejects any non-empty parameters. MongoDB's `createCollection()` supports significant options: `validator`, `capped`, `max`, `size`, `collation`, etc. There is no way to set collection-level validation rules or create capped collections via this template. Users who need these features must write programmatic changes.
- **Risk:** Feature gap rather than correctness issue. A user expecting these features from the template will get a validation error with no guidance about the limitation.
- **Fix:** Either implement `CreateCollectionOptions` support (significant scope) or document the limitation explicitly in the error message: `"createCollection does not currently accept options — use a programmatic Change for collection-level validator, capped, or other options"`.

---

## 3. Operation Coverage Matrix

| Operation | Enum Value | Operator Class | Transactional | Validation | Options Mapper | Session Handling | Unit Test | Integration Test |
|---|---|---|---|---|---|---|---|---|
| `createCollection` | `CREATE_COLLECTION` | `CreateCollectionOperator` | No | `NoParametersValidator` | None | Ignored | Yes | Yes |
| `createIndex` | `CREATE_INDEX` | `CreateIndexOperator` | No | `CreateIndexParametersValidator` | `IndexOptionsMapper` | Ignored | Yes | Yes |
| `insert` | `INSERT` | `InsertOperator` | Yes | `InsertParametersValidator` | `InsertOptionsMapper` | Conditional | Yes | Yes |
| `update` | `UPDATE` | `UpdateOperator` | Yes | `UpdateParametersValidator` | `UpdateOptionsMapper` | Conditional | Yes | Yes |
| `delete` | `DELETE` | `DeleteOperator` | Yes | `DeleteParametersValidator` | None | Conditional | Yes | Yes |
| `dropCollection` | `DROP_COLLECTION` | `DropCollectionOperator` | No | `NoParametersValidator` | None | **Ignored silently** | Yes | Yes |
| `dropIndex` | `DROP_INDEX` | `DropIndexOperator` | No | `DropIndexParametersValidator` | None | Ignored | Yes | Yes |
| `renameCollection` | `RENAME_COLLECTION` | `RenameCollectionOperator` | No | `RenameCollectionParametersValidator` | `RenameCollectionOptionsMapper` | Ignored | Yes | Yes |
| `modifyCollection` | `MODIFY_COLLECTION` | `ModifyCollectionOperator` | No | `ModifyCollectionParametersValidator` | None | Ignored | Yes | Yes |
| `createView` | `CREATE_VIEW` | `CreateViewOperator` | No | `CreateViewParametersValidator` | `CreateViewOptionsMapper` | Ignored | Yes | Yes |
| `dropView` | `DROP_VIEW` | `DropViewOperator` | No | `NoParametersValidator` | None | **Ignored silently** | Yes | Yes |

**Anomalies:**
- `dropCollection` and `dropView` are identical in implementation (both call `collection.drop()` without session). Consistent, but worth noting.
- `delete` has no options mapper, meaning MongoDB delete options (collation, hint) are not supported. This is a feature gap.
- `createCollection`, `dropCollection`, `dropView`, `dropIndex`, `createIndex` all ignore `clientSession` entirely. This is correct since they are non-transactional, and `MongoOperator.logOperation()` logs a warning when a session is present on a non-transactional operation.

**Coverage notes:**
- All 11 operations have unit/integration tests
- Transactional paths (insert, update, delete with `ClientSession`) have dedicated transactional test classes
- **Gap:** No test exercises the `bucketSize`/`wildcardProjection`/`hidden` execution-time failure (Issue 1)
- **Gap:** No test exercises null-valued option keys (Issue 3)
- **Gap:** No test exercises `renameCollection` with both source and target absent (Issue 6)
- **Gap:** No test for collation sub-field type errors (Issue 2)

---

## 4. Test Coverage Gap Analysis

### 4.1 Current Test Inventory

| Test Class | Est. Tests | Type | External Dep. |
|---|---|---|---|
| `MongoChangeTemplateTest` | 7 | Integration (full framework) | MongoDB (Docker) |
| `MongoOperationValidateTest` | ~38 | Unit | None |
| `MongoOperationGetInfoTest` | ~11 | Unit | None |
| `MongoOperatorExceptionWrappingTest` | ~2 | Unit | None |
| `CreateCollectionOperatorTest` | ~4 | Integration | MongoDB (Docker) |
| `CreateIndexOperatorTest` | ~3 | Integration | MongoDB (Docker) |
| `CreateIndexOperatorOptionsTest` | ~10 | Integration | MongoDB (Docker) |
| `CreateViewOperatorTest` | ~3 | Integration | MongoDB (Docker) |
| `DeleteOperatorTest` | ~3 | Integration | MongoDB (Docker) |
| `DeleteOperatorTransactionalTest` | ~2 | Integration | MongoDB (Docker) |
| `DropCollectionOperatorTest` | ~2 | Integration | MongoDB (Docker) |
| `DropIndexOperatorTest` | ~4 | Integration | MongoDB (Docker) |
| `DropViewOperatorTest` | ~2 | Integration | MongoDB (Docker) |
| `InsertOperatorTest` | ~3 | Integration | MongoDB (Docker) |
| `InsertOperatorOptionsTest` | ~6 | Integration | MongoDB (Docker) |
| `InsertOperatorTransactionalTest` | ~3 | Integration | MongoDB (Docker) |
| `ModifyCollectionOperatorTest` | ~3 | Integration | MongoDB (Docker) |
| `MultipleOperationsTest` | ~3 | Integration | MongoDB (Docker) |
| `RenameCollectionOperatorTest` | ~4 | Integration | MongoDB (Docker) |
| `UpdateOperatorTest` | ~3 | Integration | MongoDB (Docker) |
| `UpdateOperatorTransactionalTest` | ~2 | Integration | MongoDB (Docker) |
| `BsonConverterTest` | ~12 | Unit | None |
| `IndexOptionsMapperTest` | ~10 | Unit | None |
| `InsertOptionsMapperTest` | ~4 | Unit | None |
| `UpdateOptionsMapperTest` | ~4 | Unit | None |
| `CreateViewOptionsMapperTest` | ~3 | Unit | None |
| `RenameCollectionOptionsMapperTest` | ~2 | Unit | None |
| `MapperUtilTest` | ~8 | Unit | None |
| **Total** | **~160** | | |

### 4.2 Critical Missing Tests

#### P0 — Must have before GA

- **`IndexOptionsMapper`: unsupported option validation (Issue 1)**
  Test that `createIndex` with `options: {bucketSize: 5}` produces a `TemplatePayloadValidationError` at load time (not an `UnsupportedOperationException` at execution time). Currently this test would fail — exposing the gap.

- **Rollback payload validation parity**
  Verify that a YAML step with an invalid rollback (e.g., `type: insert` with missing `documents`) is rejected at load time via `TemplatePayload.validate()`, not discovered when the rollback is actually triggered.

- **Transactional INSERT/UPDATE/DELETE rollback path with `ClientSession`**
  Verify that rolled-back inserts/updates/deletes within a transaction are actually reverted in MongoDB. Current transactional tests confirm the session is passed; none confirm data is actually rolled back.

- **`renameCollection` when both source and target are absent**
  Should produce a clear, actionable error (Issue 6). Currently throws `MongoCommandException` wrapped in `MongoTemplateExecutionException` with the driver error message.

#### P1 — Important

- **Null-valued options in YAML (Issue 3)**
  `createIndex` with `options: {expireAfterSeconds: null}` should fail at load time with a validation error, not at execution time with an `IllegalArgumentException`.

- **Collation sub-field type errors (Issue 2)**
  `createIndex` with `options: {collation: {locale: "en", caseLevel: "yes"}}` should fail with a clear message (`ClassCastException` currently).

- **`BsonConverter` for Float type**
  Verify the `IllegalArgumentException("Unsupported BSON type: Float")` message is included in the test.

- **`modifyCollection` no-op when all three optional params are null after pipeline deserialization edge case**
  Confirm the validator rejects operations with empty/missing parameters before they reach the operator.

- **`CreateViewOperator` idempotency — view already exists**
  Already tested partially in `CreateViewOperatorTest`, but verify the skip log is emitted.

- **`createCollection` with options (the feature-gap path)**
  Verify that `createCollection` with non-empty parameters produces a validation error with a meaningful message (not just "does not accept parameters").

---

## 5. Robustness Checklist

| Criterion | Status | Details |
|---|---|---|
| Null input handling | GOOD | All validators handle null parameters/collections. `MongoOperation` helpers rely on validators having run first (documented). `DeleteParametersValidator` uses null-safe `params == null ? null :` pattern. |
| Type safety | PARTIAL | Validators enforce types for all primary parameters. Option maps: types checked via `MapperUtil.get*()` helpers for top-level keys, but collation sub-fields use raw casts (Issue 2). |
| Error collection vs fail-fast | GOOD | `MongoOperation.validate()` collects all errors before returning. TypeValidator is fail-fast (returns early on invalid type, no point validating further). Pattern is correct. |
| Exception hierarchy | GOOD | `MongoTemplateExecutionException` wraps driver exceptions with context. `IllegalArgumentException` for validation failures in mappers. Clean hierarchy. |
| Logging | GOOD | `MongoOperator.logOperation()` logs transactional mode + session mismatches. Operation operators log idempotency skips at INFO. Logger name "MongoTemplate" is consistent. |
| Immutability | PARTIAL | `MongoOperation` is mutable (setters required for YAML deserialization). Validator instances are `static final`. Option sets are `Collections.unmodifiableSet()`. Operators are effectively immutable after construction. |
| Thread safety | GOOD | No shared mutable state between operations. Validator singletons are stateless. Each step gets a new `MongoOperator` instance. |
| Idempotency | PARTIAL | `createCollection`, `createView`, `createIndex` (via MongoDB), `dropIndex`, and `renameCollection` have idempotency guards. `dropCollection` and `dropView` are implicitly idempotent (MongoDB `drop()` is no-op on non-existent). `insert`, `update`, `delete` are **not idempotent by design**. |
| Backwards compatibility | NOT HANDLED | No versioning of the YAML schema. Removing a supported field or operation would be a breaking change with no migration path. Acceptable for current stage. |
| Resource cleanup | N/A | Template borrows `MongoDatabase` from the application context; no lifecycle management needed. No connections or cursors owned by the template. |

---

## 6. Security and Safety Assessment

| Area | Status | Details |
|---|---|---|
| Name/key injection (collection names, index names, viewOn) | PARTIALLY MITIGATED | `$` and `\0` are validated. MongoDB enforces further restrictions at the driver level. In Flamingock's threat model (developer-authored YAML, not user-submitted input), full injection protection is less critical. The MongoDB driver parameterizes collection names natively, so command injection via collection name is blocked by the driver. |
| Arbitrary command execution | LOW RISK | `modifyCollection` builds a raw `collMod` MongoDB command from user-supplied YAML fields. This is developer-written YAML, not user input. The command fields (`validator`, `validationLevel`, `validationAction`) are constrained by validator logic. |
| Data destruction safety | ACCEPTABLE | `dropCollection`, `dropView` are data-destructive with no confirmation mechanism. This is by design — Flamingock changes are audited, irreversible changes should use the rollback step to recreate. No additional guard is needed. |
| Input deserialization safety | DELEGATED | YAML → Java types is handled by the Flamingock framework's Jackson-based deserialization, not by this module. `BsonConverter` handles Map/List/primitive conversion safely with explicit type checks and `IllegalArgumentException` on unsupported types. |

---

## 7. Design & Engineering Principles Evaluation

### 7.1 Right Level of Engineering

**Not overengineered: WELL APPLIED**
No unnecessary abstractions. `DatabaseInspector` is a package-private utility rather than an over-engineered interface. `MapperUtil` is a final utility class. `OperationValidator` is a functional interface (single method). No excessive configurability. The enum factory pattern in `MongoOperationType` is pragmatic — it co-locates operation name, operator factory, validator, and transactional flag in one place.

**Not underengineered: WELL APPLIED**
The separation of validators, mappers, and operators is appropriate for the complexity of 11 distinct operations. Each validator has its own class rather than a monolithic switch. `MongoOperator`'s template method pattern eliminates duplication in exception handling and logging across 11 implementations.

**Balance verdict: WELL APPLIED**
The complexity is proportional to the problem. 11 operations × (validator + mapper + operator + test) is verbose but justified — each operation has meaningfully different validation and execution logic. No layer adds overhead without value.

### 7.2 SOLID Principles

**Single Responsibility: WELL APPLIED**
Each class has one clear reason to change. `MongoOperation` owns deserialization and validation dispatch. `MongoOperationType` owns factory and enum-to-type mapping. Operators own execution. Validators own structural correctness. Mappers own type conversion.

**Open/Closed: ADEQUATE**
Adding a new operation requires: (1) new enum value in `MongoOperationType`, (2) new `MongoOperator` subclass, (3) new `OperationValidator` implementation. Existing classes do not need modification. The pattern works well, though the enum in `MongoOperationType` technically requires editing to add new entries — a registry-based pattern would be more OCP-pure but is overkill for the current scale.

**Liskov Substitution: WELL APPLIED**
All `MongoOperator` subclasses honor the contract: `applyInternal()` either executes the operation or throws `MongoTemplateExecutionException` (the base `apply()` wraps non-template exceptions). The idempotency guard (return-early pattern) is consistent across operators that use it.

**Interface Segregation: WELL APPLIED**
`OperationValidator` is a single-method functional interface. Clients depend only on `validate(MongoOperation)`. Static helpers are additive utilities on the interface, not forced dependencies.

**Dependency Inversion: ADEQUATE**
Operators depend on `MongoDatabase` (MongoDB driver concrete type). This is appropriate — the module's purpose *is* to execute MongoDB operations. No abstraction layer over the driver is warranted. The driver is injected via constructor (not instantiated inside operators).

### 7.3 Design Patterns

**Factory (MongoOperationType): WELL APPLIED**
The `BiFunction<MongoDatabase, MongoOperation, MongoOperator>` stored in each enum value is an elegant factory pattern. It avoids a traditional factory class and keeps all operation metadata together.

**Template Method (MongoOperator): WELL APPLIED**
`apply()` → `applyInternal()` correctly separates cross-cutting concerns (logging, exception wrapping) from operation-specific execution. All 11 operators benefit without code duplication.

**Strategy (OperationValidator): WELL APPLIED**
Each operation type holds its validator as a strategy. The validator is selected by `MongoOperationType` and never hardcoded in the operator. Consistent with the factory pattern.

**Missing — Registry for type lookup (MongoOperationType): NEEDS IMPROVEMENT**
`findByTypeOrThrow()` scans all enum values with `Arrays.stream()` on every call. A static `Map<String, MongoOperationType>` lookup table would be the standard pattern for this use case and makes the intent clearer. Minor concern.

### 7.4 API Design (Template Consumer Experience)

**YAML schema: ADEQUATE**
The schema is intuitive: `type`, `collection`, `parameters` with operation-specific keys. Error messages name the exact field path (`parameters.documents`, `parameters.options.unknownKey`) and explain the constraint clearly.

**Error messages: GOOD**
Validation errors include entity path and human-readable explanation. `"Insert operation requires 'documents' parameter"` is clear. `"Insert operation does not recognize option 'xyz'"` is actionable.

**Principle of least surprise: ADEQUATE**
`createCollection` and `createIndex` are idempotent (skip if exists/already exists). `dropCollection`, `dropView`, `dropIndex` are idempotent at the MongoDB driver level. `renameCollection` handles the "already renamed" case explicitly. `update` and `delete` default to single-document (no `multi: true`) which is the safe default. `insert` with a single document routes to `insertOne` automatically.

**Defaults: GOOD**
`multi: false` for update/delete is the safe default. No `transactional` default forces the user to be explicit.

### 7.5 Consistency

**WELL APPLIED overall, with minor inconsistencies:**
- Idempotency pattern is consistent for `createCollection`, `createView`, `renameCollection`, `dropIndex`. `dropCollection` and `dropView` are implicitly idempotent via MongoDB behavior (no guard needed).
- All 11 operators follow the same `applyInternal(ClientSession)` signature.
- All validators follow the same `List<TemplatePayloadValidationError> validate(MongoOperation)` signature.
- **Inconsistency:** `DeleteParametersValidator` uses inline null-safety `params == null ? null :` (line 37) while `InsertParametersValidator` returns early on null parameters. Minor but inconsistent.
- **Inconsistency:** `CreateIndexOperator` does not have an idempotency existence check while `CreateCollectionOperator` does. MongoDB handles the idempotent case natively for identical index definitions, but the inconsistency in approach may confuse future contributors.

### 7.6 Defensive Programming vs Trust Boundaries

**ADEQUATE**
Validation runs at the correct boundary: load time via `MongoOperation.validate()`. Operators trust that validation has run and do not re-validate. `MongoOperator` documents the precondition via Javadoc in `MongoChangeTemplate`. The `validateSession()` call in `MongoChangeTemplate` is the correct enforcement point for the transactional session contract.

**Gap:** The helper methods on `MongoOperation` (`getKeys()`, `getFilter()`, `getOptions()`) have implicit preconditions (validator ran) but no explicit assertion to make violations detectable (see Issue 5).

---

## 8. PR-Ready Concrete Changes

### Fix Issue 1 — Remove unsupported keys from validation or explicitly reject them

**`CreateIndexParametersValidator.java:66-75`**
After the `options instanceof Map` check, add:
```java
Set<String> unsupportedKeys = new HashSet<>(Arrays.asList("bucketSize", "wildcardProjection", "hidden"));
for (String unsupported : unsupportedKeys) {
    if (optionsMap.containsKey(unsupported)) {
        errors.add(new TemplatePayloadValidationError(
            "parameters.options." + unsupported,
            "'" + unsupported + "' is not supported — removed in MongoDB driver 4.1.0+"));
    }
}
```
Remove `bucketSize`, `wildcardProjection`, and `hidden` from `IndexOptionsMapper.RECOGNIZED_KEYS`.
Remove the `UnsupportedOperationException` throws in `IndexOptionsMapper.java:94-110` (now unreachable).

### Fix Issue 2 — Replace raw casts in `buildCollationFromMap()`

**`MapperUtil.java:107-130`**
Replace raw casts with type-checked helpers:
```java
if (map.containsKey("caseLevel")) {
    builder.caseLevel(getBoolean(map, "caseLevel"));    // was: (Boolean) map.get(...)
}
if (map.containsKey("caseFirst")) {
    builder.collationCaseFirst(CollationCaseFirst.fromString(getString(map, "caseFirst")));
}
// ... same for strength, alternate, maxVariable, normalization, backwards
```
Note: `strength` needs `getInteger(map, "strength")` to feed into `CollationStrength.fromInt()`.

### Fix Issue 6 — Handle missing-source-and-target in `RenameCollectionOperator`

**`RenameCollectionOperator.java:37-44`**
Add a case before the rename call:
```java
if (!sourceExists && !targetExists) {
    logger.warn("Neither source '{}' nor target '{}' exists, skipping renameCollection",
        op.getCollection(), targetName);
    return;
}
```

### Fix Issue 7 — Eliminate null return from `CreateViewOperator.getPipeline()`

**`CreateViewOperator.java:50-55`**
Change:
```java
return rawPipeline != null ? rawPipeline.stream().map(Document::new).collect(Collectors.toList()) : null;
```
To:
```java
if (rawPipeline == null) {
    throw new IllegalStateException("pipeline is null — validate() guarantees it is non-null");
}
return rawPipeline.stream().map(Document::new).collect(Collectors.toList());
```

### Fix Issue 8 — Log deprecation warning for `background` option

**`IndexOptionsMapper.java:52-54`**
After setting `background`:
```java
if (options.containsKey("background")) {
    indexOptions.background(getBoolean(options, "background"));
    // Add:
    logger.warn("'background' index option is deprecated since MongoDB 4.2 and has no effect in 5.0+");
}
```

### Fix Issue 9 — Static lookup map in `MongoOperationType`

**`MongoOperationType.java:75-86`**
Add a static initializer:
```java
private static final Map<String, MongoOperationType> LOOKUP;
static {
    LOOKUP = new HashMap<>();
    for (MongoOperationType t : values()) {
        LOOKUP.put(t.value, t);
    }
}

public static MongoOperationType findByTypeOrThrow(String typeValue) {
    MongoOperationType t = LOOKUP.get(typeValue);
    if (t == null) throw new IllegalArgumentException("MongoOperation not supported: " + typeValue);
    return t;
}

public static Optional<MongoOperationType> findByType(String typeValue) {
    return Optional.ofNullable(LOOKUP.get(typeValue));
}
```

---

## 9. Code Quality Observations

### Positive

- **Validation-first design is well-executed.** `MongoOperation.validate()` implements the `TemplatePayload` contract correctly: it collects all errors (not fail-fast), runs type validation first (aborting early on unknown type since all subsequent validators depend on it), then runs the collection and operation validators.

- **`MongoOperationType` enum as registry is elegant.** Placing the operator factory (`BiFunction`), the validator instance, the type name, and the transactional flag in one enum constant means adding a new operation is a single, local change. No separate registry or DI configuration needed.

- **`DatabaseInspector` encapsulates MongoDB namespace queries cleanly.** Package-private, stateless, two focused methods. Zero abstraction overhead.

- **`BsonConverter` handles the full YAML → BSON type mapping with recursive support.** Clean and correct for all YAML-representable types. The `IllegalArgumentException` for unsupported types (`Float`, arbitrary objects) is appropriate.

- **`MapperUtil` type-safe getter pattern is good.** `instanceof` checks with meaningful error messages is far superior to raw casts in mapper code.

- **Exception context in `MongoTemplateExecutionException` is well-designed.** Every execution failure includes `type` and `collection` context, making log output actionable without needing a stack trace.

- **`MongoOperator.logOperation()` has excellent operational visibility.** The four branches (transactional+session, transactional+no-session, non-transactional+session, non-transactional+no-session) cover every combination with appropriate log levels (DEBUG/WARN/INFO).

- **Validation test coverage in `MongoOperationValidateTest` is thorough.** Every validator has dedicated test cases including null, empty, wrong type, unrecognized keys, and edge cases.

### Negative

- **`IndexOptionsMapper` has dead code in the validation flow.** Three `UnsupportedOperationException` throws exist for keys that pass validation (Issue 1). The validator and mapper are out of sync.

- **`buildCollationFromMap()` does not use the established `MapperUtil` pattern.** The rest of the mapper layer uses `getBoolean(map, key)` for type-safe extraction; `buildCollationFromMap()` uses raw casts. This inconsistency is the source of Issue 2.

- **`DeleteParametersValidator` and `InsertParametersValidator` handle null `params` differently.** One uses early return, the other uses inline null-safe access. Small but breaks the consistency of the validator layer.

- **`MongoOperation` helpers (`getKeys`, `getFilter`, `getOptions`) mix their concern with the model class.** These are convenience methods for operators, but they contain operator-specific casting logic (`new Document((Map) parameters.get("keys"))`). Moving them to `BsonConverter` or as static helpers in `MongoOperator` would make `MongoOperation` a purer POJO.

- **`CreateIndexOperator` does not follow the idempotency guard pattern used by `CreateCollectionOperator` and `CreateViewOperator`.** MongoDB handles the duplicate-name-with-same-spec case silently, but a user who runs with `unique: true` and an existing non-unique index name gets a confusing MongoDB error rather than a clear idempotency message. Documenting why no guard is needed (MongoDB handles it) would help.

---

## 10. Final Score

| Category | Weight | Score (1-10) | Weighted |
|---|:---:|:---:|:---:|
| Architecture & Design | 15% | 9 | 1.35 |
| Design Principles & Right Level of Engineering | 15% | 8 | 1.20 |
| Implementation Correctness | 20% | 7 | 1.40 |
| Validation & Error Handling | 15% | 7 | 1.05 |
| Test Coverage | 15% | 8 | 1.20 |
| API Design & Consumer Experience | 10% | 8 | 0.80 |
| Security & Safety | 5% | 7 | 0.35 |
| Code Quality & Maintainability | 5% | 8 | 0.40 |
| **Total** | **100%** | | **7.75 / 10** |

### Score justification

**Architecture & Design (9/10):** The separation of validators, mappers, and operators is clean and well-proportioned. The enum factory pattern, template method in `MongoOperator`, and load-time validation via `TemplatePayload` are all well-executed architectural decisions. The 0.5-point deduction is for the static lookup gap and the minor inconsistency in idempotency patterns.

**Design Principles & Right Level of Engineering (8/10):** SOLID is largely well applied. Code is not over- or under-engineered for the problem. One point deducted for the `buildCollationFromMap()` deviation from the established `MapperUtil` pattern, and for `MongoOperation` mixing POJO responsibility with operator-specific casting.

**Implementation Correctness (7/10):** The three high/medium issues (validation-mapper gap for unsupported index options, raw casts in collation building, null option values passing validation) are genuine correctness gaps. The validation-mapper gap is particularly bad because it defeats the purpose of load-time validation. One LOW correctness gap in `RenameCollectionOperator`. Core operations (insert, update, delete) are correct.

**Validation & Error Handling (7/10):** The two-phase validation design is correct and the validator chain is well-structured. Three points deducted: unsupported index options pass validation (Issue 1), null option values are not caught (Issue 3), and collation sub-fields have no validation (Issue 2). The framework of validators is sound; these are gaps in what they check.

**Test Coverage (8/10):** Strong integration tests (one per operation), comprehensive validation unit tests, all transactional paths have dedicated test classes. Two points deducted for specific missing P0 tests: no test for the validation-mapper gap (Issue 1 would be caught by a test that asserts a validation error for `bucketSize`), and no rollback data-reversal verification.

**API Design & Consumer Experience (8/10):** Error messages are clear and specific. YAML schema is intuitive. Idempotency behavior is predictable for DDL operations. One point deducted for the misleading `bucketSize`/`wildcardProjection`/`hidden` accept-then-fail behavior. One point deducted for the lack of guidance when users want `createCollection` with options.

**Security & Safety (7/10):** In the Flamingock threat model (developer-authored YAML, not user input), the security posture is reasonable. Partial collection name validation and the raw MongoDB `collMod` command are acceptable trade-offs. Three points deducted for incomplete namespace validation and lack of explicit injection documentation.

**Code Quality & Maintainability (8/10):** Generally clean, well-named, well-commented. The license header setup and `spotlessApply` tooling ensures consistency. Deducted for the inconsistent null-params handling between validators and for the raw casts in `buildCollationFromMap()` not following the established pattern.

### Bottom line

The module is **close to production-ready**. The architecture is sound, the validation-first design is correctly implemented, and the test coverage is strong. The three blocking issues before GA are: (1) the validation-mapper gap for unsupported index options (Issue 1) — users will be misled by passing validation then failing at runtime; (2) collation sub-field type errors producing `ClassCastException` instead of structured errors (Issue 2); (3) null-valued YAML option keys bypassing load-time validation (Issue 3). Fixing these three would raise the implementation correctness and validation scores to 9/10 and lift the total to ~8.3/10. The codebase has a strong foundation and is well-structured for continued development.
