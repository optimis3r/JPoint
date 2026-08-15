import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-Subsystem Enterprise Benchmark Simulator
 * 
 * Simulates a complex microservices pipeline with 7 distinct leaking subsystems.
 * Eclipse MAT identifies multiple distinct problem suspect classes across:
 * 1. OrderIngestionBuffer
 * 2. UserEventStreamCache
 * 3. PaymentAuditLedger
 * 4. NotificationQueueManager
 * 5. MetricsCollectorRegistry
 * 6. ImageProcessingCache
 * 7. DynamicGroovyScriptCache
 */
public class DistributedPipelineCluster {

    // Subsystem 1: Order Ingestion Buffer
    public static class OrderIngestionBuffer {
        private static final List<byte[]> INGESTION_BUFFER = new ArrayList<>();
        public void bufferOrder(byte[] data) { INGESTION_BUFFER.add(data); }
    }

    // Subsystem 2: User Event Stream Cache
    public static class UserEventStreamCache {
        private static final Map<String, UserEvent> EVENT_CACHE = new ConcurrentHashMap<>();
        public static class UserEvent {
            private String id = UUID.randomUUID().toString();
            private byte[] payload = new byte[1024 * 128]; // 128 KB
        }
        public void cacheEvent(String key) { EVENT_CACHE.put(key, new UserEvent()); }
    }

    // Subsystem 3: Payment Audit Ledger
    public static class PaymentAuditLedger {
        private static final List<TransactionReceipt> LEDGER = new ArrayList<>();
        public static class TransactionReceipt {
            private String txnId = "TXN_" + UUID.randomUUID();
            private byte[] receiptData = new byte[1024 * 64]; // 64 KB
        }
        public void recordTransaction() { LEDGER.add(new TransactionReceipt()); }
    }

    // Subsystem 4: Notification Queue Manager
    public static class NotificationQueueManager {
        private static final List<NotificationMessage> QUEUE = new ArrayList<>();
        public static class NotificationMessage {
            private String msgId = "MSG_" + UUID.randomUUID();
            private byte[] content = new byte[1024 * 64]; // 64 KB
        }
        public void enqueueNotification() { QUEUE.add(new NotificationMessage()); }
    }

    // Subsystem 5: Metrics Collector Registry
    public static class MetricsCollectorRegistry {
        private static final Map<String, TimeSeriesMetric> METRICS = new ConcurrentHashMap<>();
        public static class TimeSeriesMetric {
            private String metricName;
            private byte[] rawSamples = new byte[1024 * 32]; // 32 KB
            public TimeSeriesMetric(String name) { this.metricName = name; }
        }
        public void recordMetric(String name) { METRICS.put(name, new TimeSeriesMetric(name)); }
    }

    // Subsystem 6: Image Processing Cache
    public static class ImageProcessingCache {
        private static final List<ImageFrameBuffer> FRAME_CACHE = new ArrayList<>();
        public static class ImageFrameBuffer {
            private byte[] rawBuffer = new byte[1024 * 256]; // 256 KB
        }
        public void cacheFrame() { FRAME_CACHE.add(new ImageFrameBuffer()); }
    }

    // Subsystem 7: Dynamic Script Cache
    public static class DynamicGroovyScriptCache {
        private static final List<ScriptCompilationContext> SCRIPTS = new ArrayList<>();
        public static class ScriptCompilationContext {
            private String scriptId = "SCRIPT_" + UUID.randomUUID();
            private byte[] bytecode = new byte[1024 * 16]; // 16 KB
        }
        public void compileScript() { SCRIPTS.add(new ScriptCompilationContext()); }
    }

    public static void main(String[] args) {
        System.out.println("🚀 Starting DistributedPipelineCluster (Multi-Suspect Leak Benchmark)...");

        OrderIngestionBuffer orderBuffer = new OrderIngestionBuffer();
        UserEventStreamCache userCache = new UserEventStreamCache();
        PaymentAuditLedger paymentLedger = new PaymentAuditLedger();
        NotificationQueueManager notifQueue = new NotificationQueueManager();
        MetricsCollectorRegistry metricsRegistry = new MetricsCollectorRegistry();
        ImageProcessingCache imageCache = new ImageProcessingCache();
        DynamicGroovyScriptCache scriptCache = new DynamicGroovyScriptCache();

        int count = 0;
        try {
            while (true) {
                count++;

                // Subsystem 1 (Order Ingestion - 256 KB)
                orderBuffer.bufferOrder(new byte[1024 * 256]);

                // Subsystem 2 (User Event Cache - 128 KB)
                userCache.cacheEvent("EVENT_" + count);

                // Subsystem 3 (Payment Audit Ledger - 64 KB)
                paymentLedger.recordTransaction();

                // Subsystem 4 (Notification Queue - 64 KB)
                notifQueue.enqueueNotification();

                // Subsystem 5 (Metrics Collector - 32 KB)
                metricsRegistry.recordMetric("METRIC_" + count);

                // Subsystem 6 (Image Cache - 256 KB)
                imageCache.cacheFrame();

                // Subsystem 7 (Dynamic Script Cache - 16 KB)
                scriptCache.compileScript();

                if (count % 10 == 0) {
                    System.out.println(String.format("[*] Ingestion Cycle: %d | Memory Allocated across 7 Subsystems", count));
                }

                // Sleep on every cycle to keep CPU usage low while leaking memory steadily
                Thread.sleep(10);
            }
        } catch (Throwable t) {
            System.err.println("\n🔥 OutOfMemoryError in DistributedPipelineCluster!");
            t.printStackTrace();
            throw new Error(t);
        }
    }
}
