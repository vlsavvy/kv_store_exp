# StorageEngine Overview

This document explains the architecture of the current `StorageEngine` and `Replicator` implementation.

---

## Components

| Component      | Purpose |
|----------------|---------|
| **MemTable**   | In-memory key-value store for fast writes and reads. Limited in size; flushes older data to disk via compaction in future. |
| **WAL**       | Write-Ahead Log. Ensures durability by logging every write before applying it to MemTable. Used for recovery after crashes. |
| **SSTable**    | Disk-based, immutable sorted table for persistent storage. **Currently written to during puts, but reads are not yet implemented.** |
| **Compactor**  | Background process for merging SSTables, removing deleted entries, and optimizing disk usage. **Yet to be planned and integrated.** |
| **WriteQueue** | Async queue to serialize writes, handle batching, and prevent blocking the calling thread. |
| **Replicator** | Optional in-memory replication layer. Can be extended to multi-node replication or caching. |

---

## Current Behavior

- **Write (`put`)**
    - Enqueued in `WriteQueue`
    - Logged in `WAL` for durability
    - Written to `MemTable`
    - Written to `SSTable` (disk), but **read path does not yet use SSTable**
    - Compaction scheduled (placeholder)
    - Replicator updated (optional)

- **Read (`get`)**
    - Checks **MemTable** first
    - Checks **Replicator** next (if configured)
    - SSTable reads are **not yet implemented**; fallback to disk is planned

- **Batch operations**
    - `putBatch` and `getBatch` follow similar logic
    - `getBatch` currently reads from MemTable and Replicator only

- **Crash recovery**
    - `MemTable` is restored from WAL
    - SSTable reads would be required once data exceeds memory capacity (planned)

---

## Notes / To-Do

1. **SSTable reads**: Needed for retrieving older keys that have been flushed from MemTable.
2. **Compaction**: Background merging of SSTables to remove deleted keys, maintain sorted order, and optimize disk space.
3. **Indexes / Bloom filters**: Can be added to optimize SSTable lookups.
4. **Full disk-based get**: Currently disabled; will be integrated once compaction and indexing are implemented.

---

## Summary

The current `StorageEngine` works as an **in-memory + WAL prototype** with optional replication.  
Disk-based persistence and read optimization via SSTables and compaction are **planned but not yet implemented**.
