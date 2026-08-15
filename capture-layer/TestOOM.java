import java.util.*;

public class TestOOM {
    private static final List<byte[]> leakyBucket = new ArrayList<>();

    public static void main(String args[]) throws InterruptedException {
        System.out.println("Starting TestOOM...");
        int iteration = 1;

        while(true) {
            byte[] b = new byte[1048576];
            leakyBucket.add(b);

            System.out.println("Iteration " + iteration + ": Total Size: " + leakyBucket.size() + "MB");
            iteration++;

            Thread.sleep(50);
        }
    }
}