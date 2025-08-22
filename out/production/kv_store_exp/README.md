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
```

## Examples
```curl.exe -X PUT "http://localhost:8080/kv/mykey" -d "myvalue"```
OK
```curl.exe -X POST "http://localhost:8080/batch" `              
  -H "Content-Type: application/json" `
   -d '{ "mykey1": "value1", "mykey2": "value2", "mykey3": "value3" }'```
Batch insert successful
```curl.exe -X GET "http://localhost:8080/kv/mykey3"```          
value3                                                                                                                                                                              
```curl.exe -X GET "http://localhost:8080/kv/mykey"```                                                                                 
myvalue

