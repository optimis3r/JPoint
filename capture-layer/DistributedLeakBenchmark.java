import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;

public class DistributedLeakBenchmark {

    private static final Map<String, ComplexLeakNode> ELUSIVE_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<byte[]> CORRUPTED_THREAD_LOCAL = new ThreadLocal<>();
    private static final Queue<Future<?>> ORPHANED_FUTURE_QUEUE = new ConcurrentLinkedQueue<>();

    public static class HeavyPayloadContext {
        private final byte[] allocationBlob = new byte[1024 * 768];
        private final String correlationId = UUID.randomUUID().toString();
    }

    public static class ComplexLeakNode {
        private final HeavyPayloadContext context = new HeavyPayloadContext();
        private final WeakReference<String> ephemeralKeyRef;

        public ComplexLeakNode(String key) {
            this.ephemeralKeyRef = new WeakReference<>(key);
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting DistributedLeakBenchmark...");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(6);

        // Cache flood
        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (int i = 0; i < 60; i++) {
                    String syntheticKey = "KEY-" + UUID.randomUUID().toString();
                    ELUSIVE_CACHE.put(syntheticKey, new ComplexLeakNode(syntheticKey));
                }
            } catch (Throwable t) {}
        }, 0, 50, TimeUnit.MILLISECONDS);

        // ThreadLocal leak
        ExecutorService workerPool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            workerPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        byte[] accumulation = CORRUPTED_THREAD_LOCAL.get();
                        if (accumulation == null) {
                            accumulation = new byte[1024 * 1024 * 2];
                            CORRUPTED_THREAD_LOCAL.set(accumulation);
                        }
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // Orphaned futures
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Future<?> future = workerPool.submit(() -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {}
                });
                ORPHANED_FUTURE_QUEUE.add(future);
            } catch (Throwable ignored) {}
        }, 0, 10, TimeUnit.MILLISECONDS);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Benchmark interrupted.");
        }
    }
}