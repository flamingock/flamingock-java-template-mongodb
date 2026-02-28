# Flamingock MongoDB Template - Comprehensive Module Analysis

**Module:** `flamingock-java-template-mongodb` v1.0.0-rc.1
**Flamingock Core:** v1.1.0-rc.2
**Java Target:** 8
**MongoDB Driver:** 4.0.0 (compileOnly)
**Last Updated:** 2026-02-27

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
- 8 parameter validators (`InsertParametersValidator`, `UpdateParametersValidator`, `DeleteParametersValidator`, `CreateIndexParametersValidator`, `DropIndexParametersValidator`, `RenameCollectionParametersValidator`, `CreateViewParametersValidator`, `ModifyCollectionParametersValidator`)
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
| createCollection | `CREATE_COLLECTION` | `CreateCollectionOperator` |      No       |                   collection only                   |              None               | Ignores (base class logs) | `CreateCollectionOperatorTest` (2) |          YAML `_0001`          |
| dropCollection   | `DROP_COLLECTION`   | `DropCollectionOperator`   |      No       |                   collection only                   |              None               | Ignores (base class logs) |  `DropCollectionOperatorTest` (2)  |     YAML `_0004` rollback      |
| insert           | `INSERT`            | `InsertOperator`           |      Yes      |              Full (documents, options)              |      `InsertOptionsMapper`      |           Full            |      `InsertOperatorTest` (4)      | YAML `_0002`, `_0003`, `_0005` |
| update           | `UPDATE`            | `UpdateOperator`           |      Yes      |           Full (filter, update, options)            |      `UpdateOptionsMapper`      |           Full            |      `UpdateOperatorTest` (7)      |              None              |
| delete           | `DELETE`            | `DeleteOperator`           |      Yes      |                Full (filter, multi)                 |              None               |           Full            |      `DeleteOperatorTest` (6)      |     YAML `_0002` rollback      |
| createIndex      | `CREATE_INDEX`      | `CreateIndexOperator`      |      No       |                Full (keys, options)                 |      `IndexOptionsMapper`       | Ignores (base class logs) |   `CreateIndexOperatorTest` (3)    |     YAML `_0003`, `_0005`      |
| dropIndex        | `DROP_INDEX`        | `DropIndexOperator`        |      No       |                  indexName or keys                  |              None               | Ignores (base class logs) |    `DropIndexOperatorTest` (3)     |     YAML `_0005` rollback      |
| renameCollection | `RENAME_COLLECTION` | `RenameCollectionOperator` |      No       |               Full (target, options)                | `RenameCollectionOptionsMapper` | Ignores (base class logs) | `RenameCollectionOperatorTest` (2) |              None              |
| modifyCollection | `MODIFY_COLLECTION` | `ModifyCollectionOperator` |      No       | Full (validator, validationLevel, validationAction) |              None               | Ignores (base class logs) | `ModifyCollectionOperatorTest` (2) |              None              |
| createView       | `CREATE_VIEW`       | `CreateViewOperator`       |      No       |          Full (viewOn, pipeline, options)           |    `CreateViewOptionsMapper`    | Ignores (base class logs) |    `CreateViewOperatorTest` (2)    |              None              |
| dropView         | `DROP_VIEW`         | `DropViewOperator`         |      No       |                   collection only                   |              None               | Ignores (base class logs) |     `DropViewOperatorTest` (2)     |              None              |

### Coverage Notes:
- **5 of 11 operations** have integration test coverage via YAML changes (createCollection, insert, delete, createIndex, dropIndex)
- **6 operations** are only tested at the operator unit level: update, renameCollection, modifyCollection, createView, dropView, dropCollection (though dropCollection appears in YAML rollback steps)
- **0 operations** are tested with `ClientSession` (transactional path) — framework integration tests cover this implicitly
- All 11 operations now have comprehensive load-time validation via `MongoOperation.validate()`
- All 8 DDL operators now consistently delegate session handling to the base class `MongoOperator.logOperation()`

---

## 4. Test Coverage Gap Analysis

### 4.1 Current Test Inventory

| Test Class                          | Test Count |                  Type                  | Docker Required |
|-------------------------------------|:----------:|:--------------------------------------:|:---------------:|
| `MongoChangeTemplateTest`           |     6      | Integration (full Flamingock pipeline) |       Yes       |
| `MongoOperationValidateTest`        |     72     |   Unit (pure logic, nested classes)    |       No        |
| `InsertOperatorTest`                |     4      |      Integration (operator-level)      |       Yes       |
| `UpdateOperatorTest`                |     7      |      Integration (operator-level)      |       Yes       |
| `DeleteOperatorTest`                |     6      |      Integration (operator-level)      |       Yes       |
| `CreateIndexOperatorTest`           |     3      |      Integration (operator-level)      |       Yes       |
| `DropIndexOperatorTest`             |     3      |      Integration (operator-level)      |       Yes       |
| `CreateCollectionOperatorTest`      |     2      |      Integration (operator-level)      |       Yes       |
| `DropCollectionOperatorTest`        |     2      |      Integration (operator-level)      |       Yes       |
| `RenameCollectionOperatorTest`      |     2      |      Integration (operator-level)      |       Yes       |
| `ModifyCollectionOperatorTest`      |     2      |      Integration (operator-level)      |       Yes       |
| `CreateViewOperatorTest`            |     2      |      Integration (operator-level)      |       Yes       |
| `DropViewOperatorTest`              |     2      |      Integration (operator-level)      |       Yes       |
| `MultipleOperationsTest`            |     4      |      Integration (operator-level)      |       Yes       |
| `IndexOptionsMapperTest`            |     22     |                  Unit                  |       No        |
| `MapperUtilTest`                    |     34     |                  Unit                  |       No        |
| `InsertOptionsMapperTest`           |     11     |                  Unit                  |       No        |
| `UpdateOptionsMapperTest`           |     8      |                  Unit                  |       No        |
| `RenameCollectionOptionsMapperTest` |     4      |                  Unit                  |       No        |
| `CreateViewOptionsMapperTest`       |     3      |                  Unit                  |       No        |
| **Total**                           |  **~199**  |                                        |                 |

Unit tests (no Docker): ~154 | Integration tests (Docker required): ~45

### 4.2 Remaining Test Gaps

**P0 — Transactional path:**
No dedicated tests exercise operators with a `ClientSession`. The transactional execution path is only tested implicitly through the `MongoChangeTemplateTest` integration pipeline. No test verifies transaction commit after successful apply, transaction rollback after failed apply, or behavior when `isTransactional=true` but `clientSession=null`. This is acceptable for now since the framework manages session lifecycle, but explicit tests would increase confidence.

**P1 — Options integration:**
Mapper unit tests are comprehensive (82 tests), but no operator test verifies behavior with non-default options end-to-end (e.g., `insert` with `bypassDocumentValidation`, `createIndex` with `unique`, `update` with `upsert`). The mapper tests validate conversion correctness; the gap is confirming that converted options produce the expected MongoDB behavior.

**P1 — Idempotency:**
No test verifies behavior when operations are re-applied (e.g., `createCollection` when collection already exists, `createIndex` when index already exists). This is acknowledged as a framework-level responsibility — the Flamingock audit store prevents re-execution of completed changes.

### 4.2.1 Previously Identified Gaps — Now Resolved

| Original Gap                          | Status   | How Resolved                                                                     |
|---------------------------------------|----------|----------------------------------------------------------------------------------|
| P0: Rollback validation               | RESOLVED | Framework calls `validate()` at load time for both apply and rollback payloads   |
| P0: Validation-operator alignment     | RESOLVED | All parameter validators run `instanceof` type checks before getters are invoked |
| P0: CreateIndexOperator error message | RESOLVED | Redundant warning blocks removed; base class handles logging                     |
| P1: modifyCollection tests            | RESOLVED | 13 dedicated validation tests in `MongoOperationValidateTest`                    |
| P1: Edge case getters                 | RESOLVED | Type validation at load time prevents `ClassCastException` in getters            |
| P1: deleteOne/deleteMany              | RESOLVED | `multi` parameter added and tested (6 tests in `DeleteOperatorTest`)             |
| P1: Collation YAML                    | RESOLVED | Map-to-Collation conversion added with tests in `MapperUtilTest`                 |

---

## 5. Robustness Checklist

| Criterion                       |   Status    | Details                                                                                                                                                                                                                |
|---------------------------------|:-----------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Null input handling             |    GOOD     | Validators catch null parameters at load time. `MongoOperation.validate()` handles null type, null collection, null parameters. Type checks prevent NPEs in getters                                                    |
| Type safety                     |   PARTIAL   | Top-level parameters are type-checked (`filter instanceof Map`, `documents instanceof List`, etc.), but **nested elements are not**. See section 5.1 for details                                                       |
| Error collection (vs fail-fast) |    GOOD     | Individual validators collect all errors per operation. `MongoOperation.validate()` aggregates errors from `CollectionValidator` + `OperationValidator`. Framework collects across all payloads before any change runs |
| Exception hierarchy             |    GOOD     | Uses the framework's `TemplatePayloadValidationError` with structured field/message pairs. No custom exception classes needed — the framework handles error presentation                                               |
| Logging                         |    GOOD     | `MongoOperator` base class logs transactional/non-transactional status for every operation. Clear INFO message when a non-transactional operation receives a session                                                   |
| Immutability                    |    WEAK     | `MongoOperation` is fully mutable (public setters). No defensive copying of `parameters` map. Acceptable since instances are per-change and not shared                                                                 |
| Thread safety                   |     N/A     | Template instances are per-execution, not shared. Static logger is safe                                                                                                                                                |
| Idempotency                     | NOT HANDLED | 4 of 11 operations fail on retry instead of skipping. Critical for multi-step changes with non-transactional DDL. See section 5.2                                                                                      |
| Backwards compatibility         |    GOOD     | `compileOnly` MongoDB driver 4.0.0 is a low bar. Index options correctly throw `UnsupportedOperationException` for removed options (`bucketSize`, `wildcardProjection`, `hidden`)                                      |
| Resource cleanup                |     N/A     | No resources to clean up. Operators use driver-level collections which are managed by the MongoDB client                                                                                                               |

### 5.1 Validation-to-Execution Gaps

These are cases where **YAML passes load-time validation but fails at execution** with raw `ClassCastException` or driver errors instead of clear validation messages. The root cause is that validators check top-level parameter types but not the types of elements inside collections.

#### #1 — Insert: document items not type-checked as Maps
**Severity: HIGH**
`InsertParametersValidator` checks that `documents` is a non-empty `List` and each item is non-null, but never checks `instanceof Map`. YAML like `documents: ["hello", 123]` passes validation. At execution, `Document::new` receives a String → `ClassCastException`. The user gets an opaque runtime error instead of a clear validation message pointing to the malformed document.

#### #2 — Update: `multi` parameter not type-checked
**Severity: HIGH**
`DeleteParametersValidator` validates `multi instanceof Boolean`, but `UpdateParametersValidator` does not — it only includes `multi` in `RECOGNIZED_KEYS` to avoid an "unrecognized key" error. YAML like `multi: "yes"` passes validation. At execution, `isMulti()` does `(Boolean) multi` → `ClassCastException`. Inconsistent with the delete operation's validation.

#### #3 — CreateView: pipeline elements not type-checked as Maps
**Severity: MEDIUM**
`CreateViewParametersValidator` checks `pipeline instanceof List` but not that each stage is a `Map`. YAML like `pipeline: ["invalid"]` passes validation. At execution, `Document::new` receives a String → `ClassCastException`.

#### #4 — CreateView: `viewOn` not validated for `$`/`\0`
**Severity: LOW**
`CollectionValidator` checks the `collection` field (the view name) for `$` and `\0`, and `RenameCollectionParametersValidator` checks the `target` parameter. But `viewOn` — also a collection name reference — bypasses these same checks. Inconsistent guardrails.

#### #5 — DropIndex: silently ignores `keys` when both `indexName` and `keys` provided
**Severity: LOW**
When both are present, `DropIndexOperator` uses `indexName` and silently discards `keys`. The user gets no validation error or warning that part of their YAML is being ignored. Could lead to confusion if the user intended `keys` but also included `indexName` by mistake.

### 5.2 Idempotency Gaps

No operator handles pre-existing state. This is critical for **retry scenarios** — if a multi-step change fails mid-way through non-transactional DDL operations, the framework retries from the beginning, and already-completed operations fail instead of being skipped.

| Operation          | Re-run scenario            | Current behavior          | Idempotent? | Recommendation                                                                                                                                                                                         |
|--------------------|----------------------------|---------------------------|:-----------:|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `createCollection` | Collection already exists  | `MongoCommandException`   |     No      | **MUST fix.** Check `listCollectionNames()` first, skip if exists                                                                                                                                      |
| `dropCollection`   | Collection doesn't exist   | Silent no-op              |     Yes     | No action needed                                                                                                                                                                                       |
| `createIndex`      | Identical index exists     | Silent no-op              |     Yes     | No action needed. Conflicting index (same field, different options) correctly throws — the user needs to know                                                                                          |
| `dropIndex`        | Index doesn't exist        | `MongoCommandException`   |     No      | **SHOULD fix.** Check if index exists first, skip if not                                                                                                                                               |
| `createView`       | View already exists        | `MongoCommandException`   |     No      | **SHOULD fix.** Skip if exists. The Flamingock audit store prevents re-execution of completed changes, so the retry scenario is the main concern — in that case the pipeline definition hasn't changed |
| `dropView`         | View doesn't exist         | Silent no-op              |     Yes     | No action needed                                                                                                                                                                                       |
| `renameCollection` | Source gone, target exists | `MongoCommandException`   |     No      | **SHOULD fix.** If source doesn't exist AND target exists, the rename likely already succeeded — skip. Fail otherwise                                                                                  |
| `insert`           | Duplicate `_id`            | `MongoBulkWriteException` |     No      | Leave as-is. Partial inserts make idempotency complex. User is responsible for designing idempotent inserts via rollback pairs                                                                         |
| `update`           | No docs match filter       | 0 modified, no error      |     Yes     | No action needed                                                                                                                                                                                       |
| `delete`           | No docs match filter       | 0 deleted, no error       |     Yes     | No action needed                                                                                                                                                                                       |
| `modifyCollection` | Collection doesn't exist   | `MongoCommandException`   |     No      | Leave as-is. Modifying a non-existent collection is a genuine error                                                                                                                                    |

**Summary:** 4 operations need idempotency handling (`createCollection` is the most critical), 5 are already idempotent, 2 should remain non-idempotent (the failure is meaningful).

### 5.3 Silent Validation Gaps

These are cases where **invalid or unexpected YAML is silently accepted** rather than flagged — the operation succeeds but doesn't do what the user intended.

#### #1 — `createCollection`, `dropCollection`, `dropView` accept unrecognized parameters silently
**Severity: MEDIUM**
These three operations use `OperationValidator.NO_OP`, so their `parameters` map is never inspected. YAML like `createCollection` with `parameters: { capped: true }` is silently ignored — the user thinks they created a capped collection but they didn't. Every other operation catches typos via `checkUnrecognizedKeys`, but these three don't. At minimum, they should warn when unexpected parameters are present.

#### #2 — Option mappers silently ignore unrecognized option keys
**Severity: MEDIUM**
All 5 option mappers (`IndexOptionsMapper`, `InsertOptionsMapper`, `UpdateOptionsMapper`, `CreateViewOptionsMapper`, `RenameCollectionOptionsMapper`) only process known keys and silently skip everything else. YAML like `options: { banana: true }` is discarded without any feedback. The user believes they configured an option but nothing happened. This is the same class of problem that `checkUnrecognizedKeys` solves for top-level parameters, but it's not applied inside `options`.

#### #3 — No operator wraps MongoDB driver exceptions with context
**Severity: LOW**
When a MongoDB driver operation fails (e.g., `MongoCommandException`, `MongoBulkWriteException`), the raw exception bubbles up with no indication of which operation in a multi-step change failed or what YAML produced it. For a single-step change this is manageable, but for multi-step changes with 10+ operations, debugging requires matching stack traces to YAML manually.

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
| Implementation Correctness     |   15%    |      7       |     1.05     |
| Validation & Error Handling    |   20%    |      6       |     1.20     |
| Template Feature Completeness  |   15%    |      6       |     0.90     |
| Test Coverage                  |   20%    |      6       |     1.20     |
| Security & Safety              |   10%    |      8       |     0.80     |
| Code Quality & Maintainability |    5%    |      8       |     0.40     |
| **Total**                      | **100%** |              | **6.9 / 10** |

### Score Justification

**Architecture (9/10):** Solid layered design with clean separation of concerns. The validation architecture was significantly improved by leveraging the framework's `TemplatePayload` contract — load-time validation is now built into the data model rather than being a separate step. Enum factory with validator binding is elegant. Template method pattern in operators is well-designed.

**Implementation Correctness (7/10):** All 10 original correctness issues have been resolved. Rollback validation is now handled by the framework. All operators have correct transactional flags. Collation mapping works for YAML input. Delete supports `deleteOne`/`deleteMany`. However, **4 of 11 operations are not idempotent** and will fail on retry in multi-step changes (section 5.2). `createCollection` is the most critical — it throws `MongoCommandException` if the collection already exists. No operator wraps MongoDB driver exceptions with context, making failures in multi-step changes hard to debug (section 5.3 #3).

**Validation (6/10):** Good load-time validation architecture with 8 dedicated parameter validators + `CollectionValidator`. Top-level types are checked, unrecognized keys are rejected, and multiple errors are collected. However, three layers of gaps reduce this score: (1) **nested element types are not checked** — insert documents, update `multi`, and createView pipeline stages can pass validation and produce `ClassCastException` at execution (section 5.1); (2) **3 operations accept unrecognized parameters silently** — `createCollection`, `dropCollection`, `dropView` use `NO_OP` validator (section 5.3 #1); (3) **option mappers silently ignore unrecognized keys** — `options: { banana: true }` is discarded without feedback (section 5.3 #2).

**Template Feature Completeness (6/10):** The template covers 11 MongoDB operations, which handles the most common change scenarios. However, feature gaps reduce the "no-code" value proposition: `delete` lacks `options` support (inconsistent with insert/update), `createCollection` accepts zero parameters (no capped/timeseries collections), `replaceOne` is missing entirely (semantically different from `update`), `modifyCollection` only exposes 3 of many `collMod` options, and `dropView` has no safety check against accidentally dropping real collections. See section 8 for details.

**Test Coverage (6/10):** Dramatically expanded from the original analysis. 72 validation tests cover all 11 operations with type checks, missing parameters, unrecognized keys, and error accumulation. 82 mapper unit tests cover all option conversions including collation. Operator tests expanded (UpdateOperatorTest: 7, DeleteOperatorTest: 6). However: zero transactional path tests, zero options-with-operator integration tests, zero idempotency tests, and no coverage for the validation-to-execution gaps in sections 5.1–5.3. ~199 total tests.

**Security (8/10):** Developer-authored context makes injection-style concerns not applicable. Collection name `$`/`\0` checks serve as guardrails, now applied to both `collection` and `target` parameters. `modifyCollection` parameters are validated against known values. YAML deserialization is delegated to the framework.

**Code Quality (8/10):** Clean code style, good naming, proper license headers. Four of five code quality negatives have been addressed: operator duplication flattened, `MongoOperation` god-object reduced (9 getters moved to operators), `MapperUtil` split into extraction + BSON conversion, and test boilerplate extracted to `AbstractMongoOperatorTest`. Remaining deduction: no builder/factory for `MongoOperation` in tests, and `MongoOperation` still has 4 shared getters.

### Bottom Line

The module has a **solid architecture and clean codebase**, but significant gaps remain in robustness, validation depth, and feature completeness. All 10 original correctness issues have been resolved. However, three areas need attention before the template is fully production-ready: (1) **Idempotency** — 4 DDL operations fail on retry instead of skipping, making multi-step changes fragile (section 5.2); (2) **Validation depth** — nested element types are not checked, and 3 operations plus all option mappers silently accept invalid input (sections 5.1, 5.3); (3) **Feature gaps** — missing `replaceOne`, inconsistent options support, bare-bones `createCollection` (section 8). The module is **functional for simple, single-operation changes** but needs hardening for complex multi-step scenarios.

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
