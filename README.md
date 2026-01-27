<p align="center">
  <img src="https://raw.githubusercontent.com/flamingock/flamingock-java/master/misc/logo-with-text.png" width="420px" alt="Flamingock logo" />
</p>

<h3 align="center">MongoDB Sync Template</h3>
<p align="center">Declarative, YAML-based MongoDB operations for Flamingock — no Java code required.</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.flamingock/flamingock-mongodb-sync-template">
    <img src="https://img.shields.io/maven-central/v/io.flamingock/flamingock-mongodb-sync-template" alt="Maven Version" />
  </a>
  <a href="https://github.com/flamingock/flamingock-java-template-mongodb/actions/workflows/build.yml">
    <img src="https://github.com/flamingock/flamingock-java-template-mongodb/actions/workflows/build.yml/badge.svg" alt="Build" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" />
  </a>
</p>

<p align="center">
  <a href="https://docs.flamingock.io/templates/mongodb-template"><strong>Full Documentation</strong></a>
</p>

---

## 🧩 What is this?

A Flamingock template for declarative MongoDB database operations using YAML-based change definitions. This template enables **"no-code migrations"** for MongoDB, allowing you to define database changes in YAML files instead of writing Java code.

---

## 🔑 Key Features

- **Declarative YAML-based changes** — Define MongoDB operations in simple YAML files
- **11 supported operation types** — Collections, indexes, documents, and views
- **Steps format** — Group multiple operations with paired rollbacks for atomic changes
- **Rollback support** — Optional rollback operations for reversible changes
- **Transaction support** — Configurable transactional execution
- **Java SPI integration** — Automatically discovered by Flamingock at runtime

---

## 📦 Installation

### Gradle

```kotlin
implementation("io.flamingock:flamingock-mongodb-sync-template:1.0.0")
```

### Maven

```xml
<dependency>
    <groupId>io.flamingock</groupId>
    <artifactId>flamingock-mongodb-sync-template</artifactId>
    <version>1.0.0</version>
</dependency>
```
---

## 🚀 Quick Start

### 1. Create a YAML change file

Place your change files in `src/main/resources/flamingock/changes/` (or your configured changes directory).

**Example: `_0001__create_users_collection.yaml`**

```yaml
id: create-users-collection
transactional: false
template: MongoChangeTemplate
targetSystem:
  id: "mongodb"
apply:
  type: createCollection
  collection: users
```

### 2. Insert seed data

**Example: `_0002__seed_users.yaml`**

```yaml
id: seed-users
transactional: true
template: MongoChangeTemplate
targetSystem:
  id: "mongodb"
apply:
  type: insert
  collection: users
  parameters:
    documents:
      - name: "Admin"
        email: "admin@company.com"
        roles: ["superuser"]
      - name: "Backup"
        email: "backup@company.com"
        roles: ["readonly"]
```

### 3. Multiple operations with rollback using steps

For changes that require multiple operations with paired rollbacks, use the `steps` format:

**Example: `_0003__setup_products.yaml`**

```yaml
id: setup-products
transactional: false
template: MongoChangeTemplate
targetSystem:
  id: "mongodb"

steps:
  - apply:
      type: createCollection
      collection: products
    rollback:
      type: dropCollection
      collection: products

  - apply:
      type: createIndex
      collection: products
      parameters:
        keys:
          category: 1
        options:
          name: "category_index"
    rollback:
      type: dropIndex
      collection: products
      parameters:
        indexName: "category_index"
```

---

## 📋 Supported Operations

| Operation | Type Value | Description |
|-----------|------------|-------------|
| Create Collection | `createCollection` | Creates a new collection |
| Drop Collection | `dropCollection` | Drops an existing collection |
| Rename Collection | `renameCollection` | Renames a collection |
| Modify Collection | `modifyCollection` | Modifies collection options |
| Create Index | `createIndex` | Creates an index on a collection |
| Drop Index | `dropIndex` | Drops an index from a collection |
| Insert | `insert` | Inserts one or more documents |
| Update | `update` | Updates documents matching a filter |
| Delete | `delete` | Deletes documents matching a filter |
| Create View | `createView` | Creates a view on a collection |
| Drop View | `dropView` | Drops an existing view |

---

## 📄 YAML Structure

The template supports two formats: **simple format** for single operations, and **steps format** for multiple operations with paired rollbacks.

### Simple Format

Use this format for single operations:

```yaml
# Required: Unique identifier for this change
id: my-change-id

# Optional: Author of this change
author: developer-name

# Optional: Whether to run in a transaction (default: true)
transactional: true

# Required: Template to use
template: MongoChangeTemplate

# Required: Target system configuration
targetSystem:
  id: "mongodb"

# Required: Single operation to apply
apply:
  type: <operation-type>
  collection: <collection-name>
  parameters:
    # Operation-specific parameters

# Optional: Single rollback operation
rollback:
  type: <operation-type>
  collection: <collection-name>
  parameters:
    # Operation-specific parameters
```

### Steps Format

Use this format when you need multiple operations with paired rollbacks:

```yaml
id: my-change-id
transactional: false
template: MongoChangeTemplate
targetSystem:
  id: "mongodb"

# List of steps, each with an apply and optional rollback
steps:
  - apply:
      type: <operation-type>
      collection: <collection-name>
      parameters:
        # Operation-specific parameters
    rollback:
      type: <operation-type>
      collection: <collection-name>
      parameters:
        # Operation-specific parameters

  - apply:
      type: <another-operation>
      collection: <collection-name>
    rollback:
      type: <rollback-operation>
      collection: <collection-name>
```

---

## 💡 Operation Examples

### Create Collection

```yaml
apply:
  type: createCollection
  collection: products
```

### Create Index

```yaml
apply:
  type: createIndex
  collection: products
  parameters:
    keys:
      category: 1
      price: -1
    options:
      name: "category_price_index"
      background: true
```

### Insert Documents

```yaml
apply:
  type: insert
  collection: products
  parameters:
    documents:
      - name: "Widget"
        price: 29.99
        category: "electronics"
      - name: "Gadget"
        price: 49.99
        category: "electronics"
```

### Update Documents

```yaml
apply:
  type: update
  collection: products
  parameters:
    filter:
      category: "electronics"
    update:
      $set:
        discounted: true
```

### Delete Documents

```yaml
apply:
  type: delete
  collection: products
  parameters:
    filter:
      discounted: true
```

### Rename Collection

```yaml
apply:
  type: renameCollection
  collection: oldName
  parameters:
    newName: newName
```

### Create View

```yaml
apply:
  type: createView
  collection: activeUsers
  parameters:
    viewOn: users
    pipeline:
      - $match:
          active: true
```

### Multiple Operations with Steps

When you need multiple operations with paired rollbacks:

```yaml
steps:
  - apply:
      type: createCollection
      collection: orders
    rollback:
      type: dropCollection
      collection: orders

  - apply:
      type: insert
      collection: orders
      parameters:
        documents:
          - orderId: "ORD-001"
            status: "pending"
    rollback:
      type: delete
      collection: orders
      parameters:
        filter: {}

  - apply:
      type: createIndex
      collection: orders
      parameters:
        keys:
          orderId: 1
        options:
          name: "orderId_index"
          unique: true
    rollback:
      type: dropIndex
      collection: orders
      parameters:
        indexName: "orderId_index"
```

---

## 📁 File Naming Convention

Change files are executed in alphabetical order. Use a numeric prefix to control execution order:

```
_0001__create_users_collection.yaml
_0002__seed_users.yaml
_0003__create_indexes.yaml
```

---

## ⚙️ Requirements

- Java 8 or higher
- MongoDB Java Driver 4.0.0 or higher (provided by your application)
- Flamingock Core 1.0.0 or higher

---

## 🛠️ Building from Source

```bash
./gradlew build
```

## 🧪 Running Tests

Tests require Docker for MongoDB TestContainers:

```bash
./gradlew test
```

---

## 📘 Learn more

- [Full Template Documentation](https://docs.flamingock.io/templates/mongodb-template)
- [Flamingock Documentation](https://docs.flamingock.io)
- [Core Concepts](https://docs.flamingock.io/get-started/core-concepts)
- [Examples Repository](https://github.com/flamingock/flamingock-java-examples)

---

## 🤝 Contributing

Flamingock is built in the open.

If you'd like to report a bug, suggest an improvement, or contribute code,
please see the main [Flamingock repository](https://github.com/flamingock/flamingock-java).

---

## 📢 Get involved

- Star the project to show support
- Report issues via the [issue tracker](https://github.com/flamingock/flamingock-java-template-mongodb/issues)
- Join discussions on [GitHub Discussions](https://github.com/flamingock/flamingock-java/discussions)

---

## 📜 License

This project is open source under the [Apache License 2.0](LICENSE).
