# LSMKV - Lightweight LSM-based Key-Value Store (Java Demo)

**LSMKV** is a demo **Java-based key-value store** implementing a simple **LSM (Log-Structured Merge)** architecture.
It demonstrates the core components of an LSM system including **MemTable, WAL, SSTables, compaction, backpressure**, and a **basic HTTP API**.

---

## Features

* In-memory **MemTable** with recovery from **Write-Ahead Log (WAL)**
* Persistent **SSTables** on disk
* Background **Compaction** to merge SSTables
* Simple **HTTP API** for key-value operations:

    * `/kv` for single GET/PUT/DELETE
    * `/batch` for batch insert/get/delete
    * `/range` for range scans
* **Backpressure handling** with a bounded write queue
* Optional **replication** support (Leader-Follower)
* HTTP server uses a **fixed thread pool executor** (10 threads by default)

---

## Requirements

* **Java 17+**
* No build system required (standalone Java application)

---

## Usage

1. **Compile all Java files**

```bash
javac -d bin src/main/java/lsmkv/**/*.java
```

2. **Run the main server**
### Note: `Main.java` sets up `HttpServerWrapper` on port 8080:

```bash
java -cp bin lsmkv.Main
```

---

## API Examples

### Single Key Operations

**PUT a single key:**

```bash
curl.exe -X PUT "http://localhost:8080/kv/mykey" -d "myvalue"
```

**GET a single key:**

```bash
curl.exe -X GET "http://localhost:8080/kv/mykey"
# Output: myvalue
```

---

### Batch Operations

**Batch insert keys:**

```bash
curl.exe -X POST "http://localhost:8080/batch" \
  -H "Content-Type: application/json" \
  -d '{ "mykey1": "value1", "mykey2": "value2", "mykey3": "value3" }'
# Output: Batch insert successful
```

**Batch get keys:**

```bash
curl.exe -X GET "http://localhost:8080/batch?keys=mykey1,mykey2"
# Output: {"mykey1":"value1","mykey2":"value2"}
```

---

### Range Operations

**Fetch a range of keys:**

```bash
curl.exe -X GET "http://localhost:8080/range?start=mykey1&end=mykey3"
# Output: {"mykey1":"value1","mykey2":"value2","mykey3":"value3"}
```

> Note: Range results are inclusive and returned in **MemTable key order**.

---

### Notes

* HTTP server uses a **fixed thread pool of 10 threads** for concurrent request handling.
* Currently, replication is a placeholder—future work can include **disk-based replication and follower syncing**.
* Batch and range operations support **byte\[] values**, allowing storage of arbitrary binary data.
