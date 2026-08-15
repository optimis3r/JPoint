import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EnterpriseOrderProcessor {

    // The Leak Source: A global static audit ledger that accumulates data indefinitely
    private static final Map<String, OrderTransaction> GLOBAL_AUDIT_LEDGER = new ConcurrentHashMap<>();
    private static final List<AuditPayload> MEMORY_BLOAT_HISTORY = new ArrayList<>();

    public static class CustomerProfile {
        private String customerId;
        private String email;
        private byte[] sessionTokenData; // Inflates heap size

        public CustomerProfile(String customerId, String email) {
            this.customerId = customerId;
            this.email = email;
            this.sessionTokenData = new byte[1024 * 64]; // 64 KB per profile
        }
    }

    public static class OrderTransaction {
        private String transactionId;
        private CustomerProfile customer;
        private double amount;
        private long timestamp;

        public OrderTransaction(String transactionId, CustomerProfile customer, double amount) {
            this.transactionId = transactionId;
            this.customer = customer;
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class AuditPayload {
        private byte[] rawJsonPayload; // 512 KB payload blob
        private String metadata = "JPoint-Enterprise-Audit-Node-V4";

        public AuditPayload() {
            this.rawJsonPayload = new byte[1024 * 512]; 
        }
    }

    public static void main(String[] args) {
        System.out.println("🚀 Starting Enterprise Order Processor Simulator...");
        ExecutorService executor = Executors.newFixedThreadPool(4);

        int counter = 0;
        try {
            while (true) {
                final int batchId = ++counter;
                executor.submit(() -> {
                    for (int i = 0; i < 50; i++) {
                        String txId = "TXN-" + UUID.randomUUID().toString();
                        CustomerProfile profile = new CustomerProfile("CUST-" + batchId, "user_" + batchId + "@enterprise.io");
                        OrderTransaction txn = new OrderTransaction(txId, profile, 1499.99);

                        // Intentionally leaking references into global static structures
                        GLOBAL_AUDIT_LEDGER.put(txId, txn);
                        MEMORY_BLOAT_HISTORY.add(new AuditPayload());
                    }
                });

                if (batchId % 100 == 0) {
                    System.out.println("[*] Processed ledger batches: " + batchId + " | Current Ledger Size: " + GLOBAL_AUDIT_LEDGER.size());
                    Thread.sleep(10); // Short pacing breath
                }
            }
        } catch (Throwable t) {
            System.err.println("OOM Triggered in Enterprise Order Pipeline!");
            t.printStackTrace();
            // Shutdown pool
            executor.shutdownNow();
            throw new Error(t);
        }
    }
}