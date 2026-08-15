import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Production-Level Memory Leak Scenario:
 * Multi-Tenant Session Cache & Uncleared ThreadLocal Context
 * 
 * Real-World Defects:
 * 1. Broken Cache Key Contract: Overrides equals() but forgets hashCode().
 *    Because default identity hashCode is used, every new TenantCacheKey object 
 *    calculates a different hash bucket in ConcurrentHashMap, causing endless 
 *    duplicate inserts for the same tenant/user.
 * 2. Unremoved ThreadLocal Context: CURRENT_TENANT_CONTEXT.set() is called in 
 *    reusable worker thread pool threads, but never cleaned up via .remove(), 
 *    retaining heavy session objects on thread pool threads.
 */
public class TenantContextCacheManager {

    // Global static cache for tenant session tokens and permissions
    private static final Map<TenantCacheKey, TenantSessionContext> SESSION_CACHE = new ConcurrentHashMap<>();
    
    // ThreadLocal context simulator for request processing threads
    private static final ThreadLocal<TenantSessionContext> CURRENT_TENANT_CONTEXT = new ThreadLocal<>();

    // Composite key class with a subtle production bug: equals() implemented, hashCode() missing!
    public static class TenantCacheKey {
        private final String tenantId;
        private final String userId;

        public TenantCacheKey(String tenantId, String userId) {
            this.tenantId = tenantId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TenantCacheKey other = (TenantCacheKey) obj;
            return tenantId.equals(other.tenantId) && userId.equals(other.userId);
        }

        // BUG: hashCode() is NOT overridden! Inherits Object.hashCode(),
        // causing ConcurrentHashMap to treat identical keys as distinct hash buckets.
    }

    public static class TenantSessionContext {
        private final String sessionToken;
        private final String tenantId;
        private final String userId;
        private final byte[] permissionsCacheBlob; // 256 KB memory footprint per session
        private final List<String> auditLogs = new ArrayList<>();

        public TenantSessionContext(String tenantId, String userId) {
            this.sessionToken = "token_" + UUID.randomUUID();
            this.tenantId = tenantId;
            this.userId = userId;
            this.permissionsCacheBlob = new byte[1024 * 256]; // 256 KB per context
            
            for (int i = 0; i < 50; i++) {
                auditLogs.add("AUDIT_LOG_ENTRY_" + i + "_" + System.nanoTime());
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("🚀 Booting TenantContextCacheManager (Multi-Tenant Production Leak Simulator)...");

        ExecutorService threadPool = Executors.newFixedThreadPool(8);
        int requestCount = 0;

        try {
            while (true) {
                requestCount++;
                final int reqId = requestCount;
                final String tenantId = "TENANT-" + (reqId % 10); // 10 active tenants
                final String userId = "USER-" + (reqId % 50);     // 50 active users

                threadPool.submit(() -> {
                    // Re-instantiate key for cache lookup (standard pattern in web request handlers)
                    TenantCacheKey key = new TenantCacheKey(tenantId, userId);

                    // Cache lookup or compute if absent
                    TenantSessionContext context = SESSION_CACHE.computeIfAbsent(key, k -> new TenantSessionContext(tenantId, userId));

                    // Set ThreadLocal for current request thread
                    CURRENT_TENANT_CONTEXT.set(context);

                    // Simulating request processing...
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignored) {}

                    // BUG: Missing CURRENT_TENANT_CONTEXT.remove()!
                    // ThreadLocal retains context reference on the pooled worker thread indefinitely.
                });

                if (requestCount % 100 == 0) {
                    System.out.println(String.format("[*] Requests Handled: %d | SESSION_CACHE Size: %d", 
                            requestCount, SESSION_CACHE.size()));
                    Thread.sleep(10);
                }
            }
        } catch (Throwable t) {
            System.err.println("\n🔥 OutOfMemoryError Exception in TenantContextCacheManager!");
            System.err.println("Total Cache Entries Accumulated: " + SESSION_CACHE.size());
            threadPool.shutdownNow();
            t.printStackTrace();
            throw new Error(t);
        }
    }
}
