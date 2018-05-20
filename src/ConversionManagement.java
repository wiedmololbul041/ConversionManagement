import javax.xml.transform.Result;
import java.util.Comparator;
import java.util.concurrent.*;

class DataPortionInterfaceImpl implements ConverterInterface.DataPortionInterface {
    public DataPortionInterfaceImpl(int id, ConverterInterface.Channel channel) {
        this.id = id;
        this.c = channel;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public int[] data() {
        return new int[0];
    }

    @Override
    public ConverterInterface.Channel channel() {
        return c;
    }

    int id;
    ConverterInterface.Channel c;
}

class DataPortion implements Comparable<DataPortion> {
    public DataPortion(ConverterInterface.DataPortionInterface data) {
        this.data = data;
    }

    @Override
    public int compareTo(DataPortion other)
    {
        if (data.id() != other.data.id())
            return Integer.compare(data.id(), other.data.id());
        else
            return Integer.compare(data.channel().name().length(), other.data.channel().name().length());
    }

    public String toString() {
        return "" + data.id() + " " + data.channel();
    }

    public ConverterInterface.DataPortionInterface data;
}

class ResultDataPortion extends DataPortion {
    public ResultDataPortion(ConverterInterface.DataPortionInterface data, long result) {
        super(data);
        this.result = result;
    }

    long result;
}

class Converter extends Thread {
    public Converter(ConverterInterface converter,
                     PriorityBlockingQueue<ConverterInterface.DataPortionInterface> dataQueue,
                     Semaphore killCores,
                     PriorityBlockingQueue<ResultDataPortion> resultLeftChannelQueue,
                     PriorityBlockingQueue<ResultDataPortion> resultRightChannelQueue) {
        this.converter = converter;
        this.dataQueue = dataQueue;
        this.killCores = killCores;
        this.resultLeftChannelQueue = resultLeftChannelQueue;
        this.resultRightChannelQueue = resultRightChannelQueue;

        System.out.println("Converter::Converter() " + getName() + " created");
    }

    public void run() {
        while (true) {
            try {
                if (killCores.tryAcquire()) {
                    System.out.println("C::run() Thread " + getName() + " is exiting...");
                    return;
                }

                // convert data
                ConverterInterface.DataPortionInterface data = dataQueue.poll(10, TimeUnit.MILLISECONDS);
                if (data == null)
                    continue;

                System.out.println("C::run() Thread " + getName() + " processing " + data.id() + "." + data.channel() + " ...");
                long result = converter.convert(data);

                ResultDataPortion rdp = new ResultDataPortion(data, result);
                if (data.channel() == ConverterInterface.Channel.LEFT_CHANNEL)
                    resultLeftChannelQueue.add(rdp);
                else
                    resultRightChannelQueue.add(rdp);
                System.out.println("C::run() Thread " + getName() + " processing " + data.id() + "." + data.channel() + " DONE");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    ConverterInterface converter;
    PriorityBlockingQueue<ConverterInterface.DataPortionInterface> dataQueue;
    Semaphore killCores;
    PriorityBlockingQueue<ResultDataPortion> resultLeftChannelQueue;
    PriorityBlockingQueue<ResultDataPortion> resultRightChannelQueue;
}


public class ConversionManagement implements ConversionManagementInterface {
    private static int SEM_KILL_CORE = 100;
    private static int SEM_RES_MAX_AVAILABLE = 10000;

//    PMO_Log log = new PMO_Log();


    ConversionManagement() {
        System.out.println("CM::ConversionManagement()");
        try {
            killCores.acquire(ConversionManagement.SEM_KILL_CORE);
            killCores.acquire(ConversionManagement.SEM_RES_MAX_AVAILABLE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setCores(int cores) {
        System.out.println("CM::setCores(" + cores + ")");

        int newCores = cores - maxCores;
        this.maxCores = cores;

        if (newCores < 0) {
            killCores.release(newCores);
        } else {
            Thread th = new Converter(
                    converter,
                    dataQueue,
                    killCores,
                    resultLeftChannelQueue,
                    resultRightChannelQueue);
            th.start();
        }
    }

    @Override
    public void setConverter(ConverterInterface converter) {
        System.out.println("CM::setConverter()");
        this.converter = converter;
    }

    @Override
    public void setConversionReceiver(ConversionReceiverInterface receiver) {
        System.out.println("CM::setConversionReceiver()");
        this.receiver = receiver;
    }

    @Override
    public void addDataPortion(ConverterInterface.DataPortionInterface data) {
        System.out.println("CM::addDataPortion(" + data.id() + "(" + data.channel() + ")" + ")");

        dataQueue.add(data);
    }

    ConverterInterface converter;
    ConversionReceiverInterface receiver;

    int maxCores = 0;
    Semaphore killCores = new Semaphore(ConversionManagement.SEM_KILL_CORE);
//    Semaphore resourceAvailable = new Semaphore(ConversionManagement.SEM_RES_MAX_AVAILABLE);
    PriorityBlockingQueue<ConverterInterface.DataPortionInterface> dataQueue;
    PriorityBlockingQueue<ResultDataPortion> resultLeftChannelQueue;
    PriorityBlockingQueue<ResultDataPortion> resultRightChannelQueue;

    public static void main(String[] argv) throws InterruptedException {
        System.out.println("Start");

        PriorityBlockingQueue<Integer> q = new PriorityBlockingQueue<>(50);

        q.add(10);
        q.add(4);
        System.out.println(q);

        Semaphore s = new Semaphore(2);
        System.out.println("" + s);
        s.acquire();
        s.tryAcquire();
        System.out.println("" + s);
        s.release(2);
        System.out.println("" + s);

        s.drainPermits();
        System.out.println("" + s);

        DataPortion d1l = new DataPortion(new DataPortionInterfaceImpl(1, ConverterInterface.Channel.LEFT_CHANNEL));
        DataPortion d2l = new DataPortion(new DataPortionInterfaceImpl(2, ConverterInterface.Channel.LEFT_CHANNEL));
        DataPortion d3l = new DataPortion(new DataPortionInterfaceImpl(3, ConverterInterface.Channel.LEFT_CHANNEL));
        DataPortion d4l = new DataPortion(new DataPortionInterfaceImpl(4, ConverterInterface.Channel.LEFT_CHANNEL));
        DataPortion d5l = new DataPortion(new DataPortionInterfaceImpl(5, ConverterInterface.Channel.LEFT_CHANNEL));
        DataPortion d6l = new DataPortion(new DataPortionInterfaceImpl(6, ConverterInterface.Channel.LEFT_CHANNEL));
        DataPortion d1r = new DataPortion(new DataPortionInterfaceImpl(1, ConverterInterface.Channel.RIGHT_CHANNEL));
        DataPortion d2r = new DataPortion(new DataPortionInterfaceImpl(2, ConverterInterface.Channel.RIGHT_CHANNEL));
        DataPortion d3r = new DataPortion(new DataPortionInterfaceImpl(3, ConverterInterface.Channel.RIGHT_CHANNEL));
        DataPortion d4r = new DataPortion(new DataPortionInterfaceImpl(4, ConverterInterface.Channel.RIGHT_CHANNEL));
        DataPortion d5r = new DataPortion(new DataPortionInterfaceImpl(5, ConverterInterface.Channel.RIGHT_CHANNEL));
        DataPortion d6r = new DataPortion(new DataPortionInterfaceImpl(6, ConverterInterface.Channel.RIGHT_CHANNEL));

        Comparator<DataPortion> ageComparator = new Comparator<DataPortion>() {
            public int compare(DataPortion person1, DataPortion person2) {
                int r = 2 * (person1.data.id() - person2.data.id()) ;
                if (person1.data.channel() == ConverterInterface.Channel.RIGHT_CHANNEL)
                    r += 1;

                return r;
            }
        };
        PriorityBlockingQueue<DataPortion> qq = new PriorityBlockingQueue<DataPortion>(50, ageComparator);
        System.out.println(qq);
        System.out.println("Adding d1L"); qq.add(d2l); System.out.println(qq);
        System.out.println("Adding d1L"); qq.add(d1l); System.out.println(qq);
        System.out.println("Adding d3L"); qq.add(d3l); System.out.println(qq);
        System.out.println("Adding d4L"); qq.add(d4l); System.out.println(qq);
        System.out.println("Adding d2R"); qq.add(d2r); System.out.println(qq);
        System.out.println("Adding d5L"); qq.add(d5l); System.out.println(qq);
        System.out.println("Adding d1R"); qq.add(d1r); System.out.println(qq);
        System.out.println("Adding dr$"); qq.add(d4r); System.out.println(qq);

        while (qq.size() != 0)
            System.out.println(qq.remove());

//        Integer i1l = new Integer(1);
//        Integer i2l = new Integer(2);
//        Integer i3l = new Integer(3);
//        Integer i4l = new Integer(4);
//        Integer i5l = new Integer(5);
//        Integer i6l = new Integer(6);
//        Integer i1r = new Integer(1);
//        Integer i2r = new Integer(2);
//        Integer i3r = new Integer(3);
//        Integer i4r = new Integer(4);
//        Integer i5r = new Integer(5);
//        Integer i6r = new Integer(6);


//        PriorityBlockingQueue<Integer> qqi = new PriorityBlockingQueue<>();
//        System.out.println(qq);
//        qqi.add(i2l); System.out.println(qqi);
//        qqi.add(i1l); System.out.println(qqi);
//        qqi.add(i3l); System.out.println(qqi);
//        qqi.add(i4l); System.out.println(qqi);
//        qqi.add(i2r); System.out.println(qqi);
//        qqi.add(i5l); System.out.println(qqi);
//        qqi.add(i1r); System.out.println(qqi);
//        qqi.add(i4r); System.out.println(qqi);

//        PriorityBlockingQueue<MyClass> queue = new PriorityBlockingQueue<MyClass>();
////        queue.add(new MyClass(2, "L"));
////        queue.add(new MyClass(1, "L"));
////        queue.add(new MyClass(3, "L"));
////        queue.add(new MyClass(4, "L"));
////        queue.add(new MyClass(2, "R"));
////        queue.add(new MyClass(5, "L"));
////        queue.add(new MyClass(1, "R"));
////        queue.add(new MyClass(4, "R"));
//        queue.add(new MyClass(new DataPortionInterfaceImpl(1, ConverterInterface.Channel.LEFT_CHANNEL)));
//        queue.add(new MyClass(new DataPortionInterfaceImpl(1, ConverterInterface.Channel.LEFT_CHANNEL)));
//        queue.add(new MyClass(new DataPortionInterfaceImpl(1, ConverterInterface.Channel.LEFT_CHANNEL)));
//        queue.add(new MyClass(new DataPortionInterfaceImpl(1, ConverterInterface.Channel.LEFT_CHANNEL)));
//        while (queue.size() != 0)
//            System.out.println(queue.remove());
//
//        System.out.println("Stop");
    }
}