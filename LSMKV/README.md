# LSMKV - Lightweight LSM-based Key-Value Store (Java Demo)

LSMKV is a demo **Java-based key-value store** using a simple LSM (Log-Structured Merge) architecture.  
It demonstrates the core components of an LSM system including **MemTable, WAL, SSTables, compaction, backpressure, and a basic HTTP API**.

---

## Features

- In-memory **MemTable** with recovery from **Write-Ahead Log (WAL)**
- **SSTables** on disk for persistent storage
- Background **Compaction** to merge SSTables
- Simple **HTTP API**:
    - `/kv` for single GET/PUT/DELETE
    - `/batch` for batch operations
    - `/range` for range scans
- **Backpressure handling** using bounded write queue
- Placeholder support for **replication** (Leader-Follower)

---


---

## Requirements

- **Java 17+**
- No build system required (standalone Java application)

---

## Usage

1. **Compile all Java files**

```bash
javac -d bin src/main/java/lsmkv/**/*.java

