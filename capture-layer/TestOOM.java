import java.util.*;

public class TestOOM {
    private static final List<byte[]> leakyBucket = new ArrayList<>();

    public static void main(String args[]) throws InterruptedException {
        System.out.println("Starting Project JPoint Simulation...");
        int iteration = 1;

        while(true) {
            byte[] b = new byte[1048576]; // 1MB per block
            leakyBucket.add(b);

            System.out.println("Iteration " + iteration + ": Added 1MB. Total Size: " + leakyBucket.size() + "MB");
            iteration++;

            Thread.sleep(50);
        }
    }
}