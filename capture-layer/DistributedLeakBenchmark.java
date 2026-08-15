import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;

public class DistributedLeakBenchmark {

    // Evasive Leak Vector 1: Weakly keyed cache mapping to heavily retained internal graph nodes
    private static final Map<String, ComplexLeakNode> ELUSIVE_CACHE = new ConcurrentHashMap<>();
    
    // Evasive Leak Vector 2: Background daemon thread holding onto thread-local memory references
    private static final ThreadLocal<byte[]> CORRUPTED_THREAD_LOCAL = new ThreadLocal<>();
    
    // Evasive Leak Vector 3: Asynchronous task queue accumulating orphaned futures
    private static final Queue<Future<?>> ORPHANED_FUTURE_QUEUE = new ConcurrentLinkedQueue<>();

    public static class HeavyPayloadContext {
        private final byte[] allocationBlob = new byte[1024 * 768]; // 750KB blob
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
        System.out.println("🚨 Starting Distributed Leak Benchmark (Hard Mode)...");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(6);

        // Task 1: Flooding the elusive cache with cyclical structure references
        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (int i = 0; i < 60; i++) {
                    String syntheticKey = "KEY-" + UUID.randomUUID().toString();
                    ELUSIVE_CACHE.put(syntheticKey, new ComplexLeakNode(syntheticKey));
                }
            } catch (Throwable t) {
                // Suppress to keep pressure building
            }
        }, 0, 50, TimeUnit.MILLISECONDS);

        // Task 2: Thread-local accumulation across asynchronous worker pools
        ExecutorService workerPool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            workerPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Accidental permanent allocation in thread-local context
                        byte[] accumulation = CORRUPTED_THREAD_LOCAL.get();
                        if (accumulation == null) {
                            accumulation = new byte[1024 * 1024 * 2]; // 2MB chunk per thread
                            CORRUPTED_THREAD_LOCAL.set(accumulation);
                        }
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // Task 3: Spawning orphaned futures that leak task descriptors
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

        // Keep main thread alive until OOM triggers
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Benchmark interrupted.");
        }
    }
}