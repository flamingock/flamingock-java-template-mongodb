# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**flamingock-template-mongodb** is a Flamingock template library that provides declarative, YAML-based MongoDB operations. It enables "no-code" system evolution by allowing developers to define MongoDB changes in YAML files instead of writing Java classes.

This is a **standalone repository** — it is not a module of `flamingock-java`. It depends on Flamingock core artifacts published to Maven Local or Maven Central.

### What is Flamingock?

Flamingock is a Change-as-Code (CaC) platform for audited, synchronized evolution of distributed systems. It applies versioned, auditable changes to external systems (databases, queues, configs, schemas, etc.) at application startup, in lockstep with the application lifecycle.

- **Not** a database migration tool (like Flyway or Liquibase)
- **Not** a CI/CD tool or infra-as-code (like Terraform)
- Changes are called "changes", never "migrations"

### How This Template Fits

Flamingock supports two authoring modes:
1. **Programmatic** — Java classes annotated with `@Change`, `@Apply`, `@Rollback`
2. **Declarative (Templates)** — YAML files processed by template implementations

This module implements option 2 for MongoDB. Users write YAML like:

```yaml
type: insert
collection: users
parameters:
  documents:
    - { name: "Alice", role: "admin" }
```

The template parses it into `MongoOperation` POJOs, validates them, and executes against a `MongoDatabase`.

## Build System

Standalone Gradle project with Kotlin DSL. Java 8 target.

### Common Commands

```bash
# Build
./gradlew build

# Run all tests (requires Docker for TestContainers MongoDB)
./gradlew test

# Check license headers
./gradlew spotlessCheck

# Fix license headers
./gradlew spotlessApply

# Publish to local Maven repo (for flamingock-java to consume)
./gradlew publishToMavenLocal
```

### Dependencies

- **Flamingock core artifacts** (`flamingock-core-commons`, `flamingock-processor`, etc.) at version defined by `flamingockVersion` in `build.gradle.kts`
- **MongoDB driver** `mongodb-driver-sync:4.0.0` — `compileOnly` (user provides at runtime)
- **TestContainers** for integration tests (requires Docker)
- Resolves from `mavenLocal()` first, then `mavenCentral()`

### Flamingock Core Dependency

When working on this project alongside `flamingock-java`, you must first publish core artifacts locally:

```bash
cd /path/to/flamingock-java
./gradlew publishToMavenLocal
```

Then this project picks them up via `mavenLocal()`. Keep versions in sync between both projects.

## Architecture

### Execution Flow

```
YAML → MongoOperation (deserialized) → MongoOperationValidator → MongoOperationType (enum factory) → MongoOperator subclass → MongoDB Driver
```

### Key Classes

| Class                     | Role                                                                                                                                                               |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MongoChangeTemplate`     | Entry point. Extends `AbstractChangeTemplate<Void, MongoOperation, MongoOperation>`. Annotated `@ChangeTemplate(name = "mongodb-sync-template", multiStep = true)` |
| `MongoOperation`          | POJO model deserialized from YAML. Fields: `type`, `collection`, `parameters`                                                                                      |
| `MongoOperationType`      | Enum with 11 operations + factory pattern via `BiFunction`                                                                                                         |
| `MongoOperationValidator` | Pre-execution validation. Collects all errors before throwing                                                                                                      |
| `MongoOperator`           | Abstract base for operators. Template method: `apply()` → `applyInternal()`                                                                                        |
| `ValidationError`         | Structured error with entityId/entityType/message                                                                                                                  |

### Supported Operations (11)

| Operation          | Transactional | Key Parameters                                     |
|--------------------|:-------------:|----------------------------------------------------|
| `createCollection` |      No       | collection name only                               |
| `dropCollection`   |      No       | collection name only                               |
| `renameCollection` |      No       | `target`                                           |
| `modifyCollection` |      No       | `validator`, `validationLevel`, `validationAction` |
| `createIndex`      |      No       | `keys`, `options`                                  |
| `dropIndex`        |      No       | `indexName` or `keys`                              |
| `insert`           |      Yes      | `documents`, `options`                             |
| `update`           |      Yes      | `filter`, `update`, `multi`, `options`             |
| `delete`           |      Yes      | `filter`                                           |
| `createView`       |      No       | `viewOn`, `pipeline`                               |
| `dropView`         |      No       | collection name only                               |

### Package Structure

```
io.flamingock.template.mongodb
├── MongoChangeTemplate.java              # Template entry point
├── mapper/                                # YAML Map → MongoDB Options conversion
│   ├── IndexOptionsMapper
│   ├── InsertOptionsMapper
│   ├── UpdateOptionsMapper
│   ├── CreateViewOptionsMapper
│   ├── RenameCollectionOptionsMapper
│   └── MapperUtil                        # Type-safe extraction utilities
├── model/
│   ├── MongoOperation                    # YAML payload POJO
│   ├── MongoOperationType                # Enum factory (11 types)
│   └── operator/                         # 11 MongoOperator subclasses
└── validation/
    ├── MongoOperationValidator            # Comprehensive validation
    ├── ValidationError                    # Error model
    └── MongoTemplateValidationException   # Exception wrapper
```

### TemplatePayload and Two-Phase Validation

`TemplatePayload` is the contract that all APPLY and ROLLBACK generic types in `ChangeTemplate<CONFIG, APPLY, ROLLBACK>` must implement. It has a single method: `validate()` returning `List<TemplatePayloadValidationError>`.

**Motivation — early validation at load time:**

Flamingock's pipeline lifecycle has two distinct phases: **load time** (YAML parsed → typed objects built) and **execution time** (template methods run against real systems). `TemplatePayload.validate()` enables structural validation at load time, so a malformed YAML change at step 50 is caught before steps 1-49 execute.

The framework calls `validate()` on every payload during the loaded-change validation phase (`SimpleTemplateLoadedChange.validateApplyPayload()` / `MultiStepTemplateLoadedChange.validateApplyPayload()`), collecting all errors before any change runs.

**How it works in this project:**

`MongoOperation implements TemplatePayload`. Its `validate()` method is the hook for the framework to validate each operation's structure (type, collection, parameters) at load time.

Currently `validate()` returns an empty list — the existing validation lives in `MongoOperationValidator` and runs at execution time inside `MongoChangeTemplate.apply()`. The next task is to **move structural validation into `MongoOperation.validate()`** so the framework catches errors early. `MongoOperationValidator` may still handle execution-time concerns, but the structural checks (missing type, invalid collection name, missing required parameters) belong in `validate()`.

**Type bound:** `ChangeTemplate` enforces `APPLY_FIELD extends TemplatePayload` at the generic level — you cannot create a template with payload types that don't implement it.

**Built-in implementations in flamingock-java:**
- `TemplateString` — wraps `String` payloads (used by `SqlTemplate`), validates non-null/non-blank
- Test-only stubs that return empty error lists

### SPI Registration

Discovered automatically via Java ServiceLoader:
```
META-INF/services/io.flamingock.api.template.ChangeTemplate
→ io.flamingock.template.mongodb.MongoChangeTemplate
```

## Testing

### Test Categories

| Category | Location | Requires Docker |
|----------|----------|:---:|
| Integration (full pipeline) | `MongoChangeTemplateTest` | Yes |
| Operator tests (1 per operation) | `operations/` | Yes |
| Multi-operation tests | `operations/MultipleOperationsTest` | Yes |
| Validator unit tests (~38) | `validation/MongoOperationValidatorTest` | No |
| Mapper unit tests | `mapper/*Test` | No |

### Test Resources

- `src/test/resources/flamingock/pipeline.yaml` — test pipeline configuration
- Test change YAML files in `src/test/java/io/flamingock/template/mongodb/changes/`

### Running Tests

All integration tests use TestContainers with MongoDB, so **Docker must be running**.

```bash
# All tests
./gradlew test

# Only unit tests (no Docker needed)
./gradlew test --tests "io.flamingock.template.mongodb.validation.*" --tests "io.flamingock.template.mongodb.mapper.*"

# Specific operator test
./gradlew test --tests "io.flamingock.template.mongodb.operations.InsertOperatorTest"
```

## Known Issues

See `ANALYSIS.md` for a detailed quality assessment. Key issues:

1. **Rollback path skips validation** — `MongoChangeTemplate.rollback()` doesn't call `MongoOperationValidator.validate()` before executing
2. **InsertOperator silently swallows null/empty documents** — redundant guard bypasses validation
3. **modifyCollection has no parameter validation** — validator returns empty error list
4. **Type-unsafe parameter extraction** — `@SuppressWarnings("unchecked")` raw casts in `MongoOperation` getters
5. **CreateIndexOperator claims transactional but ignores session** — misleading behavior
6. **Collation mapping broken for YAML input** — `MapperUtil.getCollation()` expects `Collation` object, gets `Map`

## Commit Message Convention

All commits must follow [Conventional Commits](https://www.conventionalcommits.org/) with a well-structured body suitable for changelog extraction from `git log`.
DON'T add Claude as co-author

## Terminology

- Use "**changes**", not "migrations"
- Use "**system evolution**", not "database migration"
- The template name is `mongodb-sync-template` (as declared in `@ChangeTemplate` annotation)

## License

Apache License 2.0. All source files must include the Flamingock license header. Use `./gradlew spotlessApply` to add missing headers.
