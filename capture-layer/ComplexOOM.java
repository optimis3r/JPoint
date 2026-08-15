import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComplexOOM {
    static List<String> stringLeak = new ArrayList<>();
    static Map<Integer, byte[]> mapLeak = new HashMap<>();
    static byte[] massiveArrayLeak;

    public static void main(String[] args) {
        System.out.println("Starting ComplexOOM...");
        int counter = 0;

        try {
            while (true) {
                stringLeak.add("LeakyString-ID-" + counter + "-This-Takes-Up-Space-In-The-Heap");

                if (counter % 5 == 0) {
                    mapLeak.put(counter, new byte[1024 * 5]);
                }

                if (counter == 120000) {
                     System.out.println("Allocating array bomb...");
                     massiveArrayLeak = new byte[1024 * 1024 * 50]; 
                }

                counter++;
                if (counter % 20000 == 0) {
                    System.out.println("[*] Iterations completed: " + counter);
                }
            }
        } catch (OutOfMemoryError e) {
            System.err.println("\nOutOfMemoryError Triggered at iteration: " + counter);
            try {
                Thread.sleep(10000); 
            } catch (InterruptedException ignored) {}
        }
    }
}