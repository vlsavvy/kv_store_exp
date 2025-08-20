// TODO: Implement Replicator.java
package lsmkv.replication;

public interface Replicator {
    void replicatePut(String key, byte[] value);
    void replicateDel(String key);
}
