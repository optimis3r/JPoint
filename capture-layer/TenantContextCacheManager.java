import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TenantContextCacheManager {

    private static final Map<TenantCacheKey, TenantSessionContext> SESSION_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<TenantSessionContext> CURRENT_TENANT_CONTEXT = new ThreadLocal<>();

    // Cache key
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
    }

    public static class TenantSessionContext {
        private final String sessionToken;
        private final String tenantId;
        private final String userId;
        private final byte[] permissionsCacheBlob;
        private final List<String> auditLogs = new ArrayList<>();

        public TenantSessionContext(String tenantId, String userId) {
            this.sessionToken = "token_" + UUID.randomUUID();
            this.tenantId = tenantId;
            this.userId = userId;
            this.permissionsCacheBlob = new byte[1024 * 256];
            
            for (int i = 0; i < 50; i++) {
                auditLogs.add("AUDIT_LOG_ENTRY_" + i + "_" + System.nanoTime());
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Booting TenantContextCacheManager...");

        ExecutorService threadPool = Executors.newFixedThreadPool(8);
        int requestCount = 0;

        try {
            while (true) {
                requestCount++;
                final int reqId = requestCount;
                final String tenantId = "TENANT-" + (reqId % 10);
                final String userId = "USER-" + (reqId % 50);

                threadPool.submit(() -> {
                    TenantCacheKey key = new TenantCacheKey(tenantId, userId);
                    TenantSessionContext context = SESSION_CACHE.computeIfAbsent(key, k -> new TenantSessionContext(tenantId, userId));
                    CURRENT_TENANT_CONTEXT.set(context);

                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignored) {}
                });

                if (requestCount % 100 == 0) {
                    System.out.println(String.format("[*] Requests Handled: %d | Cache Size: %d", 
                            requestCount, SESSION_CACHE.size()));
                    Thread.sleep(10);
                }
            }
        } catch (Throwable t) {
            System.err.println("\nOutOfMemoryError in TenantContextCacheManager!");
            threadPool.shutdownNow();
            t.printStackTrace();
            throw new Error(t);
        }
    }
}
