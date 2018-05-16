import java.util.concurrent.PriorityBlockingQueue;

class DataPart {
    public int
}

public class ConversionManagement {
    public static void main(String[] argv) {
        System.out.println("Start");

        PriorityBlockingQueue<Integer> q = new PriorityBlockingQueue<>(50);

        q.add(10);
        q.add(4);
        System.out.println(q);

        System.out.println("Stop");
    }
}
