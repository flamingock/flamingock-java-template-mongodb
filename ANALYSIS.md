# Flamingock MongoDB Template - Comprehensive Module Analysis

**Module:** `flamingock-java-template-mongodb` v1.0.0-rc.1  
**Flamingock Core:** v1.2.0  
**Java Target:** 8  
**MongoDB Driver:** 4.0.0 (compileOnly)  
**Last Updated:** 2026-03-02  

---

## 1. Architecture Overview

The module implements a declarative MongoDB change template for the Flamingock system evolution framework. Users define MongoDB operations in YAML files with apply/rollback step pairs. The framework parses YAML into `MongoOperation` POJOs, validates them at load time via the `TemplatePayload` contract, resolves the appropriate operator, and executes against a `MongoDatabase`.

**Execution flow:**
```
YAML → MongoOperation (deserialized) → MongoOperation.validate() → MongoOperationType (enum factory) → MongoOperator subclass → MongoDB Driver
```

Structural validation runs at **load time** — before any change executes — via `MongoOperation.validate()` (implementing `TemplatePayload`). The framework calls `validate()` on both apply and rollback payloads through `AbstractTemplateLoadedChange.getValidationErrors()`. This means a malformed YAML change at step 50 is caught before steps 1–49 execute.

**Key classes (~30 production source files):**
- `MongoChangeTemplate` — Entry point, `@ChangeTemplate(multiStep = true)`
- `MongoOperation` — YAML model (type, collection, parameters) + `TemplatePayload.validate()`
- `MongoOperationType` — Enum with 11 operations + factory + validator binding
- `CollectionValidator` — Validates collection name (null, empty, `$`, `\0`)
- `OperationValidator` — Interface for per-operation parameter validators + unrecognized key utility
- 8 parameter validators (`InsertParametersValidator`, `UpdateParametersValidator`, `DeleteParametersValidator`, `CreateIndexParametersValidator`, `DropIndexParametersValidator`, `RenameCollectionParametersValidator`, `CreateViewParametersValidator`, `ModifyCollectionParametersValidator`) + `NoParametersValidator` for operations that don't accept parameters
- 11 operator classes (`CreateCollectionOperator`, `InsertOperator`, etc.)
- 7 mapper classes (`IndexOptionsMapper`, `InsertOptionsMapper`, `UpdateOptionsMapper`, `CreateViewOptionsMapper`, `RenameCollectionOptionsMapper`, `MapperUtil`, `BsonConverter`)

---

## 2. Top 10 Issues (Ranked by Severity) — ALL RESOLVED

All 10 issues identified in the original analysis have been resolved through multiple PRs.

### #1 - ~~CRITICAL: Rollback path skips validation entirely~~ RESOLVED
**Original issue:** `MongoChangeTemplate.rollback()` did not call `MongoOperationValidator.validate()` before executing, so malformed rollback YAML ran unchecked.
**Resolution:** Structural validation was moved into `MongoOperation.validate()` (implementing the `TemplatePayload` interface). The Flamingock framework now calls `validate()` on both apply and rollback payloads at load time via `AbstractTemplateLoadedChange.getValidationErrors()`, which invokes `validateApplyPayload()` and `validateRollbackPayload()` before any change executes. The separate `MongoOperationValidator`, `ValidationError`, and `MongoTemplateValidationException` classes were deleted as part of this refactoring. Validation is no longer a responsibility of the template's `apply()`/`rollback()` methods — the framework handles it uniformly for all payloads.

### #2 - ~~HIGH: InsertOperator silently swallows null/empty documents, bypassing validation~~ RESOLVED
**Original issue:** `InsertOperator.applyInternal()` had a redundant guard `if(op.getDocuments() == null || op.getDocuments().isEmpty()) { return; }` that silently did nothing instead of failing when documents were missing.
**Resolution:** The silent guard was removed from `InsertOperator.applyInternal()`. Structural validation now runs at load time via `InsertParametersValidator` (called from `MongoOperation.validate()`), which catches null, empty, and malformed documents before any change executes. The operator no longer needs a defensive check — if documents are invalid, the framework rejects the change at load time.

### #3 - ~~HIGH: `modifyCollection` has ZERO parameter validation~~ RESOLVED
**Original issue:** `modifyCollection` used `OperationValidator.NO_OP` in `MongoOperationType`, so none of its three parameters (`validator`, `validationLevel`, `validationAction`) were validated. A user could pass `validationLevel: "banana"` and it would pass validation, only to fail at MongoDB runtime.
**Resolution:** `ModifyCollectionParametersValidator` now validates at load time: requires at least one recognized parameter, checks `validator` is a document (Map), checks `validationLevel` is one of `off`/`strict`/`moderate`, and checks `validationAction` is one of `error`/`warn`. Multiple errors accumulate. Wired into `MongoOperationType.MODIFY_COLLECTION` replacing the `NO_OP` validator.

### #4 - ~~HIGH: Type-unsafe parameter extraction throughout MongoOperation~~ RESOLVED
**Original issue:** Every parameter getter in `MongoOperation` uses `@SuppressWarnings("unchecked")` raw casts from `Map<String, Object>`, risking `ClassCastException` at runtime if YAML contained wrong-typed values. Additionally, `getOptions()` was called by operators but never validated.
**Resolution:** All parameter validators now include `instanceof` type checks that run at load time via `MongoOperation.validate()` (the `TemplatePayload` contract). Specifically: `UpdateParametersValidator` and `DeleteParametersValidator` check that `filter` is a `Map`; `DropIndexParametersValidator` checks that `keys` is a `Map` and `indexName` is a `String`; and the 5 validators for operations that support options (`InsertParametersValidator`, `UpdateParametersValidator`, `CreateIndexParametersValidator`, `CreateViewParametersValidator`, `RenameCollectionParametersValidator`) check that `options` is a `Map` when present. Since the framework guarantees `validate()` runs before any operator executes, the `@SuppressWarnings("unchecked")` casts in getters are now effectively safe — they can never be reached with wrong-typed data.

### #5 - ~~HIGH: CreateIndexOperator claims `transactional=true` but ignores the session~~ RESOLVED
**Original issue:** Constructor passed `super(mongoDatabase, operation, true)`, declaring itself transactional. But `applyInternal()` warned about "createCollection operation" (copy-paste error — should say "createIndex") and then ignored the `clientSession` entirely. MongoDB index operations are DDL and do not participate in transactions, so the flag was incorrect.
**Resolution:** Changed constructor to `super(mongoDatabase, operation, false)` to match all other DDL operators. Fixed the warning message from "createCollection operation" to "createIndex operation". The per-operator warning blocks were later removed entirely (see issue #6) since the base class `MongoOperator.logOperation()` already handles session mismatch logging uniformly.

### #6 - ~~MEDIUM: DDL operators have inconsistent session handling~~ RESOLVED
**Original issue:** `CreateCollectionOperator` and `CreateIndexOperator` had redundant `if (clientSession != null) { logger.warn(...) }` blocks, while the other 6 DDL operators silently ignored the session. The base class `MongoOperator.logOperation()` already handles this case with an INFO log.
**Resolution:** Removed the redundant warning blocks from `CreateCollectionOperator` and `CreateIndexOperator`. All 8 DDL operators now consistently trust the base class logging. Also removed the duplicate logger declaration from `CreateCollectionOperator` (see issue #10).

### #7 - ~~MEDIUM: `getCollation()` in MapperUtil will always fail for YAML-sourced input~~ RESOLVED
**Original issue:** `getCollation()` only accepted `Collation` instances but YAML always deserializes to `Map<String, Object>`, making the `collation` option in `IndexOptionsMapper`, `UpdateOptionsMapper`, and `CreateViewOptionsMapper` impossible to use from YAML.
**Resolution:** Added `else if (value instanceof Map)` branch to `getCollation()` with a `buildCollationFromMap()` private method that handles all 9 Collation builder fields (`locale`, `caseLevel`, `caseFirst`, `strength`, `numericOrdering`, `alternate`, `maxVariable`, `normalization`, `backwards`). Tests added for Map with locale only, Map with all fields, and invalid type.

### #8 - ~~MEDIUM: DeleteOperator always uses `deleteMany`, no `deleteOne` support~~ RESOLVED
**Original issue:** `DeleteOperator` always called `deleteMany()` with no way to delete a single document.
**Resolution:** Added `multi` parameter support to `DeleteOperator`, following the same pattern as `UpdateOperator`. Default is `false` (`deleteOne`), matching MongoDB's native default. Users must explicitly set `multi: true` for `deleteMany`. `DeleteParametersValidator` validates that `multi` is a boolean when present. Existing YAML test files updated to set `multi: true` where `deleteMany` behavior is intended.

### #9 - ~~MEDIUM: Unknown YAML fields are silently accepted~~ RESOLVED
**Original issue:** Unknown parameter keys (typos like `documets` instead of `documents`) were silently accepted within `parameters`.
**Resolution:** Added a static `checkUnrecognizedKeys()` utility method to `OperationValidator` interface. Each of the 7 non-NO_OP validators now declares a `RECOGNIZED_KEYS` set and calls this utility at the end of validation. Unrecognized keys produce a validation error like `"Insert operation does not recognize parameter 'unknownKey'"`. Top-level keys only — nested fields inside `options`, `filter`, `documents`, etc. are not validated (they're opaque MongoDB driver domain). NO_OP validators (createCollection, dropCollection, dropView) are left as-is since those operations don't expect parameters. Tests added for all 8 operation types with validators.

### #10 - ~~LOW: Duplicate logger field in CreateCollectionOperator~~ RESOLVED
**Original issue:** `CreateCollectionOperator` declared its own `logger` which shadowed the parent's `MongoOperator.logger`, causing inconsistent logger names for the same operation.
**Resolution:** Removed the duplicate logger declaration and unused imports from `CreateCollectionOperator`. It now inherits the parent's logger.

---

## 3. Operation Coverage Matrix

| Operation        | Enum Value          | Operator Class             | Transactional |                     Validation                      |         Options Mapper          |     Session Handling      |           Operator Test            |        Integration Test        |
|------------------|---------------------|----------------------------|:-------------:|:---------------------------------------------------:|:-------------------------------:|:-------------------------:|:----------------------------------:|:------------------------------:|
| createCollection | `CREATE_COLLECTION` | `CreateCollectionOperator` |      No       |            NoParametersValidator                    |              None               | Ignores (base class logs) | `CreateCollectionOperatorTest` (4) |          YAML `_0001`          |
| dropCollection   | `DROP_COLLECTION`   | `DropCollectionOperator`   |      No       |            NoParametersValidator                    |              None               | Ignores (base class logs) |  `DropCollectionOperatorTest` (2)  |     YAML `_0004` rollback      |
| insert           | `INSERT`            | `InsertOperator`           |      Yes      |              Full (documents, options)              |      `InsertOptionsMapper`      |           Full            |      `InsertOperatorTest` (3) + Txn (5) + Options (3)      | YAML `_0002`, `_0003`, `_0005` |
| update           | `UPDATE`            | `UpdateOperator`           |      Yes      |           Full (filter, update, options)            |      `UpdateOptionsMapper`      |           Full            |      `UpdateOperatorTest` (7) + Txn (3)      |              None              |
| delete           | `DELETE`            | `DeleteOperator`           |      Yes      |                Full (filter, multi)                 |              None               |           Full            |      `DeleteOperatorTest` (5) + Txn (3)      |     YAML `_0002` rollback      |
| createIndex      | `CREATE_INDEX`      | `CreateIndexOperator`      |      No       |                Full (keys, options)                 |      `IndexOptionsMapper`       | Ignores (base class logs) |   `CreateIndexOperatorTest` (4) + Options (3)    |     YAML `_0003`, `_0005`      |
| dropIndex        | `DROP_INDEX`        | `DropIndexOperator`        |      No       |                  indexName or keys                  |              None               | Ignores (base class logs) |    `DropIndexOperatorTest` (7)     |     YAML `_0005` rollback      |
| renameCollection | `RENAME_COLLECTION` | `RenameCollectionOperator` |      No       |               Full (target, options)                | `RenameCollectionOptionsMapper` | Ignores (base class logs) | `RenameCollectionOperatorTest` (6) |              None              |
| modifyCollection | `MODIFY_COLLECTION` | `ModifyCollectionOperator` |      No       | Full (validator, validationLevel, validationAction) |              None               | Ignores (base class logs) | `ModifyCollectionOperatorTest` (5) |              None              |
| createView       | `CREATE_VIEW`       | `CreateViewOperator`       |      No       |          Full (viewOn, pipeline, options)           |    `CreateViewOptionsMapper`    | Ignores (base class logs) |    `CreateViewOperatorTest` (4)    |              None              |
| dropView         | `DROP_VIEW`         | `DropViewOperator`         |      No       |            NoParametersValidator                    |              None               | Ignores (base class logs) |     `DropViewOperatorTest` (2)     |              None              |

### Coverage Notes:
- **5 of 11 operations** have integration test coverage via YAML changes (createCollection, insert, delete, createIndex, dropIndex)
- **6 operations** are only tested at the operator unit level: update, renameCollection, modifyCollection, createView, dropView, dropCollection (though dropCollection appears in YAML rollback steps)
- **3 transactional operators** (insert, update, delete) now have dedicated `ClientSession` tests — 11 tests across 3 transactional test classes exercise session-with-commit, session-with-options, and session-with-abort paths
- **Options integration** tested end-to-end: insert (`bypassDocumentValidation`, `ordered`), createIndex (`expireAfterSeconds`, `sparse`, `partialFilterExpression`), update (`collation`)
- `MongoChangeTemplate.validateSession()` tested for both apply and rollback paths
- All 11 operations now have comprehensive load-time validation via `MongoOperation.validate()`, including unrecognized option key detection for the 5 operations that support `options`
- All 8 DDL operators now consistently delegate session handling to the base class `MongoOperator.logOperation()`
- All operators wrap MongoDB driver exceptions with template-level context via `MongoTemplateExecutionException`

---

## 4. Test Coverage Gap Analysis

### 4.1 Current Test Inventory

| Test Class                          | Test Count |                  Type                  | Docker Required |
|-------------------------------------|:----------:|:--------------------------------------:|:---------------:|
| `MongoChangeTemplateTest`           |     7      | Integration (full Flamingock pipeline) |       Yes       |
| `MongoOperationValidateTest`        |     94     |   Unit (pure logic, nested classes)    |       No        |
| `InsertOperatorTest`                |     3      |      Integration (operator-level)      |       Yes       |
| `InsertOperatorTransactionalTest`   |     5      |   Integration (transactional path)     |       Yes       |
| `InsertOperatorOptionsTest`         |     3      |    Integration (options end-to-end)    |       Yes       |
| `UpdateOperatorTest`                |     7      |      Integration (operator-level)      |       Yes       |
| `UpdateOperatorTransactionalTest`   |     3      |   Integration (transactional path)     |       Yes       |
| `DeleteOperatorTest`                |     5      |      Integration (operator-level)      |       Yes       |
| `DeleteOperatorTransactionalTest`   |     3      |   Integration (transactional path)     |       Yes       |
| `CreateIndexOperatorTest`           |     4      |      Integration (operator-level)      |       Yes       |
| `CreateIndexOperatorOptionsTest`    |     3      |    Integration (options end-to-end)    |       Yes       |
| `DropIndexOperatorTest`             |     6      |      Integration (operator-level)      |       Yes       |
| `CreateCollectionOperatorTest`      |     3      |      Integration (operator-level)      |       Yes       |
| `DropCollectionOperatorTest`        |     2      |      Integration (operator-level)      |       Yes       |
| `RenameCollectionOperatorTest`      |     5      |      Integration (operator-level)      |       Yes       |
| `ModifyCollectionOperatorTest`      |     5      |      Integration (operator-level)      |       Yes       |
| `CreateViewOperatorTest`            |     3      |      Integration (operator-level)      |       Yes       |
| `DropViewOperatorTest`              |     2      |      Integration (operator-level)      |       Yes       |
| `MultipleOperationsTest`            |     3      |      Integration (operator-level)      |       Yes       |
| `IndexOptionsMapperTest`            |     22     |                  Unit                  |       No        |
| `MapperUtilTest`                    |     17     |                  Unit                  |       No        |
| `BsonConverterTest`                 |     28     |                  Unit                  |       No        |
| `InsertOptionsMapperTest`           |     11     |                  Unit                  |       No        |
| `UpdateOptionsMapperTest`           |     8      |                  Unit                  |       No        |
| `RenameCollectionOptionsMapperTest` |     4      |                  Unit                  |       No        |
| `CreateViewOptionsMapperTest`       |     3      |                  Unit                  |       No        |
| `MongoOperatorExceptionWrappingTest`|     5      |                  Unit                  |       No        |
| **Total**                           |  **~265**  |                                        |                 |

Unit tests (no Docker): ~192 | Integration tests (Docker required): ~73

### 4.2 Remaining Test Gaps — ALL RESOLVED

**P0 — ~~Transactional path~~** RESOLVED:
11 dedicated transactional tests across 3 test classes (`InsertOperatorTransactionalTest`, `UpdateOperatorTransactionalTest`, `DeleteOperatorTransactionalTest`) now exercise all `clientSession != null` branches. Tests cover session-with-commit, session-with-options, and session-with-abort (proving the session is actually passed through to the driver). `AbstractTransactionalOperatorTest` provides shared session lifecycle. Additionally, 2 tests in `MongoChangeTemplateTest` verify that `validateSession()` throws `IllegalArgumentException` when `isTransactional=true` and `clientSession=null` for both apply and rollback paths.

**P1 — ~~Options integration~~** RESOLVED:
9 end-to-end options tests now verify that YAML-mapped options produce the expected MongoDB behavior: `InsertOperatorOptionsTest` (3 tests: `bypassDocumentValidation`, `ordered: false`, `ordered: true`), `CreateIndexOperatorOptionsTest` (3 tests: TTL `expireAfterSeconds`, `sparse`, `partialFilterExpression`), and `UpdateOperatorTest` (+1 test: `collation` with case-insensitive matching via `locale: en, strength: 2`).

**P1 — ~~Idempotency~~** RESOLVED:
12 idempotency tests now cover all 4 DDL operators that needed handling: `CreateCollectionOperatorTest` (+2), `DropIndexOperatorTest` (+4), `CreateViewOperatorTest` (+2), `RenameCollectionOperatorTest` (+4). Tests verify both the idempotent skip path and the error path for genuine conflicts.

**P2 — ~~DDL operator gaps~~** RESOLVED:
8 additional tests fill DDL gaps: `DropCollectionOperatorTest` (+1: native idempotency of drop on non-existent collection), `DropViewOperatorTest` (+1: same for views), `CreateIndexOperatorTest` (+2: identical index idempotency, conflicting index name with different keys), `ModifyCollectionOperatorTest` (+4: validation enforcement, metadata assertions for `validationLevel`/`validationAction`, partial parameters, idempotent re-application).

### 4.2.1 Previously Identified Gaps — Now Resolved

| Original Gap                          | Status   | How Resolved                                                                     |
|---------------------------------------|----------|----------------------------------------------------------------------------------|
| P0: Rollback validation               | RESOLVED | Framework calls `validate()` at load time for both apply and rollback payloads   |
| P0: Validation-operator alignment     | RESOLVED | All parameter validators run `instanceof` type checks before getters are invoked (including nested element types) |
| P0: CreateIndexOperator error message | RESOLVED | Redundant warning blocks removed; base class handles logging                     |
| P1: modifyCollection tests            | RESOLVED | 13 dedicated validation tests in `MongoOperationValidateTest`                    |
| P1: Edge case getters                 | RESOLVED | Type validation at load time prevents `ClassCastException` in getters            |
| P1: deleteOne/deleteMany              | RESOLVED | `multi` parameter added and tested (6 tests in `DeleteOperatorTest`)             |
| P1: Collation YAML                    | RESOLVED | Map-to-Collation conversion added with tests in `MapperUtilTest`                 |
| P0: Transactional path                | RESOLVED | 11 dedicated tests + `AbstractTransactionalOperatorTest` base class              |
| P0: `validateSession()` untested      | RESOLVED | 2 tests in `MongoChangeTemplateTest` for apply and rollback paths                |
| P1: Options integration               | RESOLVED | 9 end-to-end tests: bypass validation, ordered, TTL, sparse, partial filter, collation |
| P2: DDL operator gaps                 | RESOLVED | 8 tests: drop idempotency, createIndex idempotency/conflict, modifyCollection gaps |

---

## 5. Robustness Checklist

| Criterion                       |   Status    | Details                                                                                                                                                                                                                |
|---------------------------------|:-----------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Null input handling             |    GOOD     | Validators catch null parameters at load time. `MongoOperation.validate()` handles null type, null collection, null parameters. Type checks prevent NPEs in getters                                                    |
| Type safety                     |    GOOD     | Top-level parameters and nested elements are type-checked. Insert documents and createView pipeline stages are validated as Maps via `checkListElementTypes()`. Update `multi` is validated as Boolean. See section 5.1 (all resolved) |
| Error collection (vs fail-fast) |    GOOD     | Individual validators collect all errors per operation. `MongoOperation.validate()` aggregates errors from `CollectionValidator` + `OperationValidator`. Framework collects across all payloads before any change runs |
| Exception hierarchy             |    GOOD     | Uses the framework's `TemplatePayloadValidationError` with structured field/message pairs for validation. `MongoTemplateExecutionException` wraps driver exceptions with operation type and collection context           |
| Logging                         |    GOOD     | `MongoOperator` base class logs transactional/non-transactional status for every operation. Clear INFO message when a non-transactional operation receives a session                                                   |
| Immutability                    |    WEAK     | `MongoOperation` is fully mutable (public setters). No defensive copying of `parameters` map. Acceptable since instances are per-change and not shared                                                                 |
| Thread safety                   |     N/A     | Template instances are per-execution, not shared. Static logger is safe                                                                                                                                                |
| Idempotency                     |    GOOD     | All 4 DDL operations that failed on retry now check MongoDB state first and skip if the operation already completed. Uses `DatabaseInspector` helper. See section 5.2 (all resolved)                                    |
| Backwards compatibility         |    GOOD     | `compileOnly` MongoDB driver 4.0.0 is a low bar. Index options correctly throw `UnsupportedOperationException` for removed options (`bucketSize`, `wildcardProjection`, `hidden`)                                      |
| Resource cleanup                |     N/A     | No resources to clean up. Operators use driver-level collections which are managed by the MongoDB client                                                                                                               |

### 5.1 Validation-to-Execution Gaps — ALL RESOLVED

All 5 gaps identified below have been resolved. Validators now check nested element types and parameter value constraints, preventing `ClassCastException` at execution time.

#### #1 — ~~Insert: document items not type-checked as Maps~~ RESOLVED
**Severity: HIGH**
**Original issue:** `InsertParametersValidator` checked that `documents` is a non-empty `List` and each item is non-null, but never checked `instanceof Map`. YAML like `documents: ["hello", 123]` passed validation and produced `ClassCastException` at execution.
**Resolution:** Replaced the manual null-check loop with `OperationValidator.checkListElementTypes()`, which validates both null and non-Map elements. Each invalid item produces a clear error like `"Document at index 0 must be a document (key-value map)"`.

#### #2 — ~~Update: `multi` parameter not type-checked~~ RESOLVED
**Severity: HIGH**
**Original issue:** `DeleteParametersValidator` validated `multi instanceof Boolean`, but `UpdateParametersValidator` did not. YAML like `multi: "yes"` passed validation and produced `ClassCastException` in `isMulti()`.
**Resolution:** Added `instanceof Boolean` check for `multi` in `UpdateParametersValidator`, matching the existing pattern in `DeleteParametersValidator`. Invalid types produce `"'multi' must be a boolean"`.

#### #3 — ~~CreateView: pipeline elements not type-checked as Maps~~ RESOLVED
**Severity: MEDIUM**
**Original issue:** `CreateViewParametersValidator` checked `pipeline instanceof List` but not that each stage is a `Map`. YAML like `pipeline: ["invalid"]` passed validation and produced `ClassCastException` at execution.
**Resolution:** Added `OperationValidator.checkListElementTypes()` call after confirming pipeline is a List. Invalid stages produce `"Pipeline stage at index 0 must be a document (key-value map)"`.

#### #4 — ~~CreateView: `viewOn` not validated for `$`/`\0`~~ RESOLVED
**Severity: LOW**
**Original issue:** `CollectionValidator` checked the `collection` field for `$` and `\0`, and `RenameCollectionParametersValidator` checked `target`, but `viewOn` bypassed these checks.
**Resolution:** Restructured `viewOn` validation to check type (`instanceof String`), empty value, `$`, and `\0` characters — matching the pattern used by `RenameCollectionParametersValidator` for `target`.

#### #5 — ~~DropIndex: silently ignores `keys` when both `indexName` and `keys` provided~~ RESOLVED
**Severity: LOW**
**Original issue:** When both were present, `DropIndexOperator` used `indexName` and silently discarded `keys`.
**Resolution:** Added mutual exclusivity check in `DropIndexParametersValidator`. Providing both `indexName` and `keys` now produces `"DropIndex operation requires either 'indexName' or 'keys', not both"`.

### 5.2 Idempotency Gaps — ALL RESOLVED

All 4 DDL operations that failed on retry now implement pre-check idempotency via `DatabaseInspector`, a package-private utility class with static methods for inspecting MongoDB state. Each operator checks whether the operation already completed and skips with an INFO log if so. Genuine conflicts (e.g., both source and target exist for rename) are not masked — they still fail.

| Operation          | Re-run scenario            | Current behavior                   | Idempotent? | Resolution                                                                                                                                                                                    |
|--------------------|----------------------------|------------------------------------|:-----------:|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `createCollection` | Collection already exists  | Skips with INFO log                |   **Yes**   | **RESOLVED.** `DatabaseInspector.collectionExists()` check before `createCollection()`                                                                                                       |
| `dropCollection`   | Collection doesn't exist   | Silent no-op                       |     Yes     | No action needed                                                                                                                                                                              |
| `createIndex`      | Identical index exists     | Silent no-op                       |     Yes     | No action needed. Conflicting index (same field, different options) correctly throws — the user needs to know                                                                                 |
| `dropIndex`        | Index doesn't exist        | Skips with INFO log                |   **Yes**   | **RESOLVED.** `DatabaseInspector.indexExistsByName()` / `indexExistsByKeys()` check before `dropIndex()`                                                                                     |
| `createView`       | View already exists        | Skips with INFO log                |   **Yes**   | **RESOLVED.** `DatabaseInspector.collectionExists()` check (views appear in `listCollectionNames()`)                                                                                         |
| `dropView`         | View doesn't exist         | Silent no-op                       |     Yes     | No action needed                                                                                                                                                                              |
| `renameCollection` | Source gone, target exists | Skips with INFO log                |   **Yes**   | **RESOLVED.** Checks both source and target existence. `!sourceExists && targetExists` → skip (already renamed). All other error cases proceed normally and MongoDB handles the error         |
| `insert`           | Duplicate `_id`            | `MongoBulkWriteException`          |     No      | Leave as-is. Partial inserts make idempotency complex. User is responsible for designing idempotent inserts via rollback pairs                                                                |
| `update`           | No docs match filter       | 0 modified, no error               |     Yes     | No action needed                                                                                                                                                                              |
| `delete`           | No docs match filter       | 0 deleted, no error                |     Yes     | No action needed                                                                                                                                                                              |
| `modifyCollection` | Collection doesn't exist   | `MongoCommandException`            |     No      | Leave as-is. Modifying a non-existent collection is a genuine error                                                                                                                           |

**Summary:** 9 of 11 operations are now idempotent. The remaining 2 (`insert`, `modifyCollection`) intentionally remain non-idempotent — their failure on retry is meaningful and should not be suppressed.

### 5.3 Silent Validation Gaps — ALL RESOLVED

All 3 gaps identified below have been resolved. Operations now reject unrecognized parameters and option keys at load time, and MongoDB driver exceptions are wrapped with template-level context.

#### #1 — ~~`createCollection`, `dropCollection`, `dropView` accept unrecognized parameters silently~~ RESOLVED
**Severity: MEDIUM**
**Original issue:** These three operations used `OperationValidator.NO_OP`, so their `parameters` map was never inspected. YAML like `createCollection` with `parameters: { capped: true }` was silently ignored.
**Resolution:** Replaced `OperationValidator.NO_OP` with `NoParametersValidator` for all three operations. Non-null, non-empty parameters now produce `"X operation does not accept parameters"`. Empty parameters (`{}`) and null are still accepted. The `NO_OP` constant was removed from `OperationValidator`.

#### #2 — ~~Option mappers silently ignore unrecognized option keys~~ RESOLVED
**Severity: MEDIUM**
**Original issue:** All 5 option mappers only processed known keys and silently skipped everything else. YAML like `options: { banana: true }` was discarded without feedback.
**Resolution:** Added `RECOGNIZED_KEYS` constant to all 5 option mappers (`IndexOptionsMapper`: 20 keys, `InsertOptionsMapper`: 2, `UpdateOptionsMapper`: 4, `CreateViewOptionsMapper`: 1, `RenameCollectionOptionsMapper`: 1). Added `checkUnrecognizedOptionKeys()` utility to `OperationValidator`. All 5 parameter validators that handle `options` now validate option keys when the options map is valid, producing errors like `"X operation does not recognize option 'key'"`.

#### #3 — ~~No operator wraps MongoDB driver exceptions with context~~ RESOLVED
**Severity: LOW**
**Original issue:** Raw MongoDB driver exceptions bubbled up with no indication of which operation in a multi-step change failed.
**Resolution:** `MongoOperator.apply()` now wraps `applyInternal()` exceptions in `MongoTemplateExecutionException`, which includes the operation type and collection name: `"Failed to execute '<type>' on collection '<collection>': <causeMessage>"`. Already-wrapped exceptions are re-thrown without double-wrapping. The original exception is preserved as the cause.

---

## 6. Security and Safety Assessment

> **Context:** Flamingock templates are authored by **developers**, not end users. YAML change files are committed to version control alongside application code and reviewed through the same PR process. This makes injection-style attacks (analogous to SQL injection) not applicable — the template author controls all inputs. The checks below exist as **developer guardrails** (catching mistakes), not as a security boundary against untrusted input.

### 6.1 Collection Name Validation
**Status: GOOD**
`CollectionValidator` checks for `$` and `\0` in collection names, preventing accidental use of MongoDB operator prefixes (e.g., `$cmd`) and null-byte issues. The `target` parameter in `renameCollection` is also validated for `$` and `\0` characters via `RenameCollectionParametersValidator`. Collection name length and `system.` prefix are not validated — these are MongoDB server-enforced constraints that produce clear error messages.

### 6.2 Arbitrary Command Execution
**Status: LOW RISK**
`ModifyCollectionOperator` uses `mongoDatabase.runCommand()`, which is the most powerful MongoDB operation. However, the command is built programmatically with `collMod` prefix, so it cannot be repurposed for arbitrary commands. The `validator`, `validationLevel`, and `validationAction` values are validated at load time by `ModifyCollectionParametersValidator`.

### 6.3 Data Destruction Safety
**Status: ACCEPTABLE**
- `delete` with `filter: {}` deletes ALL documents (by design, documented)
- `dropCollection` is irreversible
- No confirmation or dry-run mode exists
- Rollback provides the safety net, and rollback payloads are validated at load time just like apply payloads

### 6.4 YAML Deserialization
**Status: DELEGATED**
The module does not handle YAML parsing — it receives already-deserialized `MongoOperation` POJOs from the Flamingock framework. YAML deserialization safety is the framework's responsibility.

---

## 7. PR-Ready Concrete Changes — ALL COMPLETED

All 7 changes from the original analysis have been implemented across multiple PRs:

1. **Rollback validation** — Moved to framework-level `TemplatePayload.validate()` (PR #8, #11)
2. **InsertOperator silent guard** — Removed (PR #11)
3. **CreateIndexOperator transactional flag** — Corrected to `false`, warning removed (PR #10)
4. **modifyCollection validation** — `ModifyCollectionParametersValidator` added (PR #8)
5. **RenameCollection target validation** — `$` and `\0` checks added to `RenameCollectionParametersValidator`
6. **Collation YAML mapping** — Map-to-Collation conversion added to `MapperUtil` (PR #8)
7. **Duplicate logger removal** — Removed from `CreateCollectionOperator` (PR #11)

---

## 8. Template Feature Gaps

The following are gaps at the **feature/template level** — not code quality issues, but limitations in what the template offers to users authoring YAML changes.

### #1 — `delete` operation has no `options` support
**Severity: MEDIUM**
`insert` and `update` both support an `options` parameter (collation, bypass validation, etc.), but `delete` does not. A user has no way to specify collation for delete operations. This is an inconsistency in the template's API surface — all three DML operations should offer the same options capabilities.

### #2 — `createCollection` accepts zero parameters
**Severity: MEDIUM**
Users cannot create capped collections (`capped`, `size`, `max`), timeseries collections, or set collection-level collation. These are common setup patterns that force fallback to programmatic changes, undermining the "no-code" premise of the template.

### #3 — Missing `replaceOne` operation
**Severity: MEDIUM**
`update` modifies fields via operators (`$set`, `$unset`, etc.), but `replaceOne` replaces an entire document. These are semantically different MongoDB operations. A user who needs to replace a full document cannot express that in YAML.

### #4 — `modifyCollection` only exposes 3 of many `collMod` options
**Severity: LOW**
Only `validator`, `validationLevel`, and `validationAction` are supported. Missing `expireAfterSeconds` (TTL modification), `changeStreamPreAndPostImages`, and others. This limits what collection modifications users can declare without falling back to programmatic changes.

### #5 — `dropView` has no safety check against dropping real collections
**Severity: LOW**
`dropView` presumably calls `collection.drop()` with no verification that the target is actually a view. A user who accidentally provides a real collection name would silently destroy data. A pre-execution check against `listCollections` metadata could guard against this.

---

## 9. Code Quality Observations

### Positive
- Clean separation of concerns: template / model / validation / operators / mappers
- Enum-based factory pattern in `MongoOperationType` is elegant and extensible
- Validation architecture cleanly leverages the `TemplatePayload` contract for early error detection
- Individual per-operation validators follow a consistent pattern and collect all errors
- Template method pattern in `MongoOperator` with `apply()` / `applyInternal()` is well-designed
- Good use of `@NonLockGuarded` on `MongoOperation` model
- Comprehensive Javadoc on `MongoChangeTemplate`
- Test YAML files serve as excellent documentation of the YAML schema
- Unrecognized parameter key detection catches typos at load time

### Negative
- ~~Heavy code duplication in `InsertOperator` and `UpdateOperator`~~ RESOLVED — Flattened nested if/else to single chain; extracted options to local variable to avoid duplicate mapper calls
- ~~`MongoOperation` is a god object~~ REDUCED — Moved 9 single-use getters to their respective operator classes as private methods. `MongoOperation` retains 4 shared getters (`getOptions`, `getFilter`, `isMulti`, `getKeys`) used by multiple operators
- No builder pattern or factory for `MongoOperation` in tests — all tests manually construct via setters
- ~~`MapperUtil` mixes concerns: type extraction + BSON conversion + Collation building~~ RESOLVED — Split into `MapperUtil` (parameter extraction + collation) and `BsonConverter` (BSON serialization)
- ~~Test infrastructure duplication~~ RESOLVED — Extracted `AbstractMongoOperatorTest` base class with shared MongoDBContainer setup; 12 test classes now extend it

---

## 10. Final Score

| Category                       |  Weight  | Score (1-10) |   Weighted   |
|--------------------------------|:--------:|:------------:|:------------:|
| Architecture & Design          |   15%    |      9       |     1.35     |
| Implementation Correctness     |   15%    |      9       |     1.35     |
| Validation & Error Handling    |   20%    |      8       |     1.60     |
| Template Feature Completeness  |   15%    |      6       |     0.90     |
| Test Coverage                  |   20%    |      8       |     1.60     |
| Security & Safety              |   10%    |      8       |     0.80     |
| Code Quality & Maintainability |    5%    |      8       |     0.40     |
| **Total**                      | **100%** |              | **8.00 / 10** |

### Score Justification

**Architecture (9/10):** Solid layered design with clean separation of concerns. The validation architecture was significantly improved by leveraging the framework's `TemplatePayload` contract — load-time validation is now built into the data model rather than being a separate step. Enum factory with validator binding is elegant. Template method pattern in operators is well-designed.

**Implementation Correctness (9/10):** All 10 original correctness issues have been resolved. Rollback validation is now handled by the framework. All operators have correct transactional flags. Collation mapping works for YAML input. Delete supports `deleteOne`/`deleteMany`. MongoDB driver exceptions are now wrapped with template-level context (`MongoTemplateExecutionException`) for easier debugging of multi-step changes. All 4 DDL operations that previously failed on retry (`createCollection`, `dropIndex`, `createView`, `renameCollection`) now implement idempotent pre-checks via `DatabaseInspector` — 9 of 11 operations are now retry-safe (section 5.2). The remaining deduction is for `insert` not being idempotent (by design) and edge cases in option value type checking at the mapper level.

**Validation (8/10):** Comprehensive load-time validation architecture with 8 dedicated parameter validators + `NoParametersValidator` + `CollectionValidator`. Top-level types are checked, unrecognized parameter keys are rejected, nested element types are validated (insert documents, createView pipeline stages checked as Maps; update `multi` checked as Boolean), and multiple errors are collected. All 5 validation-to-execution gaps (section 5.1) and all 3 silent validation gaps (section 5.3) have been resolved. `NoParametersValidator` now catches unexpected parameters on `createCollection`/`dropCollection`/`dropView`. Unrecognized option keys inside `options` are detected via `checkUnrecognizedOptionKeys` with `RECOGNIZED_KEYS` sets in all 5 mappers. The remaining deduction is for edge cases in option value validation (e.g., `unique: "yes"` instead of `true` is not caught at load time — only at mapper invocation).

**Template Feature Completeness (6/10):** The template covers 11 MongoDB operations, which handles the most common change scenarios. However, feature gaps reduce the "no-code" value proposition: `delete` lacks `options` support (inconsistent with insert/update), `createCollection` accepts zero parameters (no capped/timeseries collections), `replaceOne` is missing entirely (semantically different from `update`), `modifyCollection` only exposes 3 of many `collMod` options, and `dropView` has no safety check against accidentally dropping real collections. See section 8 for details.

**Test Coverage (8/10):** Comprehensive across all layers. 94 validation tests cover all 11 operations with type checks, missing parameters, unrecognized parameter keys, unrecognized option keys, no-parameter enforcement, nested element type checks, and error accumulation. 82 mapper unit tests cover all option conversions including collation. 5 exception wrapping tests verify `MongoTemplateExecutionException` behavior. 11 transactional path tests across 3 dedicated test classes exercise `ClientSession` commit, abort, and session-with-options branches for all 3 transactional operators. 9 options integration tests verify end-to-end behavior of `bypassDocumentValidation`, `ordered`, `expireAfterSeconds`, `sparse`, `partialFilterExpression`, and `collation`. 2 `validateSession()` tests confirm `IllegalArgumentException` for transactional changes with null session. DDL gap tests cover idempotency for dropCollection, dropView, createIndex, and modifyCollection (enforcement, metadata assertions, partial parameters, re-application). ~265 total tests. The remaining deduction is for missing end-to-end tests for `arrayFilters`, `collation` on createIndex, and full Flamingock pipeline tests with transactional changes.

**Security (8/10):** Developer-authored context makes injection-style concerns not applicable. Collection name `$`/`\0` checks serve as guardrails, now applied to both `collection` and `target` parameters. `modifyCollection` parameters are validated against known values. YAML deserialization is delegated to the framework.

**Code Quality (8/10):** Clean code style, good naming, proper license headers. Four of five code quality negatives have been addressed: operator duplication flattened, `MongoOperation` god-object reduced (9 getters moved to operators), `MapperUtil` split into extraction + BSON conversion, and test boilerplate extracted to `AbstractMongoOperatorTest`. Remaining deduction: no builder/factory for `MongoOperation` in tests, and `MongoOperation` still has 4 shared getters.

### Bottom Line

The module has a **solid architecture and clean codebase**, with comprehensive validation, error handling, retry safety, and test coverage. All 10 original correctness issues, all 5 validation-to-execution gaps, all 3 silent validation gaps, all 4 idempotency gaps, and all test coverage gaps (transactional path, options integration, DDL operator gaps) have been resolved. MongoDB driver exceptions are now wrapped with template-level context for easier debugging. 9 of 11 operations are now idempotent — multi-step changes with non-transactional DDL operations are retry-safe. Test coverage spans 265 tests including transactional path verification, end-to-end options integration, and DDL idempotency. The remaining area for improvement is **feature gaps** — missing `replaceOne`, inconsistent options support, bare-bones `createCollection` (section 8). The module is **production-ready for simple and moderately complex changes** with robust multi-step retry behavior.

---

## 11. Future Roadmap

### Automatic Rollback Generation (v1.2+)

**Concept:** Rollback payloads remain mandatory by default. For operations with deterministic, safe inverses, the template auto-generates the rollback when the user omits it. For all other operations, the user must provide explicit rollback YAML — otherwise validation fails at load time. Documentation clearly states which operations support auto-rollback.

**Phase 1 — Deterministic inverses (no state required):**

| Apply | Auto-generated rollback |
|---|---|
| `createCollection` | `dropCollection` |
| `createIndex` | `dropIndex` (same keys/name) |
| `createView` | `dropView` |
| `renameCollection` | `renameCollection` (swap source/target) |

These 4 operations have clean inverses — the apply YAML contains everything needed to construct the rollback. No runtime state required.

**Phase 2 — State-aware inverses (requires framework support):**

Once the framework supports passing state between apply and rollback (or inter-execution context), additional operations become candidates:

- `insert` with explicit `_id` values → `delete` with `{_id: {$in: [...]}}`
- `modifyCollection` → restore previous validation settings (captured before apply)
- `dropIndex` by keys → `createIndex` with original keys + options (captured before drop)

**Operations that remain manual-rollback-only:**

- `update` — requires capturing previous document state
- `delete` — requires capturing deleted documents
- `dropCollection` — data loss is irreversible
- `dropView` — requires capturing the original pipeline definition

**Design principles:**
- Safe by default: rollback is mandatory unless the operation is proven auto-rollbackable
- Best-effort expansion: each phase adds more auto-rollback support, but only for operations that are fully safe
- User override: even for auto-rollback operations, explicit rollback YAML takes precedence
