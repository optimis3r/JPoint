import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComplexOOM {
    // Leak Suspect 1: A runaway list of strings
    static List<String> stringLeak = new ArrayList<>();

    // Leak Suspect 2: A bloated HashMap
    static Map<Integer, byte[]> mapLeak = new HashMap<>();

    // Leak Suspect 3: A massive static array
    static byte[] massiveArrayLeak;

    public static void main(String[] args) {
        System.out.println("🚀 Starting Complex OOM Simulator...");
        int counter = 0;

        try {
            while (true) {
                // 1. Drip-feed the String leak
                stringLeak.add("LeakyString-ID-" + counter + "-This-Takes-Up-Space-In-The-Heap");

                // 2. Drip-feed the Map leak (larger chunks, less frequent)
                if (counter % 5 == 0) {
                    mapLeak.put(counter, new byte[1024 * 5]); // 5KB chunks
                }

                // 3. The Sudden Spike (Wait until the heap is stressed, then drop a 50MB bomb)
                if (counter == 120000) {
                     System.out.println("💣 Dropping the 50MB Array Bomb...");
                     massiveArrayLeak = new byte[1024 * 1024 * 50]; 
                }

                counter++;
                if (counter % 20000 == 0) {
                    System.out.println("[*] Iterations completed: " + counter);
                }
            }
        } catch (OutOfMemoryError e) {
            System.err.println("\n[!] OutOfMemoryError Triggered at iteration: " + counter);
            System.err.println("[*] JVM is dying. Holding process open for capture script...");
            try {
                // Keep the JVM alive just long enough for your upload_script.sh to grab the .hprof
                Thread.sleep(10000); 
            } catch (InterruptedException ignored) {}
        }
    }
}