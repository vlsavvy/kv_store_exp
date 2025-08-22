// TODO: Implement WriteQueue.java
package src.main.java.lsmkv.backpressure;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class WriteQueue {
    private final BlockingQueue<Runnable> queue;

    public WriteQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public void enqueue(Runnable task) {
        queue.offer(task);
    }

    public void shutdown() {
        queue.clear();
    }
}
