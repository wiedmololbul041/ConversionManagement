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
    public Converter(ConversionManagement cm,
                     PriorityBlockingQueue<DataPortion> dataQueue,
                     Semaphore killCores,
                     PriorityBlockingQueue<ResultDataPortion> resultLeftChannelQueue,
                     PriorityBlockingQueue<ResultDataPortion> resultRightChannelQueue) {
        this.cm = cm;
        this.dataQueue = dataQueue;
        this.killCores = killCores;
        this.resultLeftChannelQueue = resultLeftChannelQueue;
        this.resultRightChannelQueue = resultRightChannelQueue;

//        System.out.println("Converter::Converter() " + getName() + " created");
    }

    public void run() {
        while (true) {
            try {
                if (killCores.tryAcquire()) {
//                    System.out.println("C::run() Thread " + getName() + " is exiting...");
                    return;
                }

                // convert data
                DataPortion data = dataQueue.poll(10, TimeUnit.MILLISECONDS);
                if (data == null)
                    continue;

//                System.out.println("C::run() Thread " + getName() + " processing " + data.data.id() + "." + data.data.channel() + " ...");
                long result = cm.converter.convert(data.data);

                ResultDataPortion rdp = new ResultDataPortion(data.data, result);
                if (data.data.channel() == ConverterInterface.Channel.LEFT_CHANNEL)
                    resultLeftChannelQueue.add(rdp);
                else
                    resultRightChannelQueue.add(rdp);
//                System.out.println("C::run() Thread " + getName() + " processing " + data.data.id() + "." + data.data.channel() + " DONE");

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    ConversionManagement cm;
    PriorityBlockingQueue<DataPortion> dataQueue;
    Semaphore killCores;
    PriorityBlockingQueue<ResultDataPortion> resultLeftChannelQueue;
    PriorityBlockingQueue<ResultDataPortion> resultRightChannelQueue;
}

class ResultReceiver extends Thread {
    public ResultReceiver(
            PriorityBlockingQueue<ResultDataPortion> resultLeftChannelQueue,
            PriorityBlockingQueue<ResultDataPortion> resultRightChannelQueue,
            ConversionManagement cm
    ) {
        this.resultLeftChannelQueue = resultLeftChannelQueue;
        this.resultRightChannelQueue = resultRightChannelQueue;
        this.cm = cm;
    }

    public void run() {
        while (true) {
            if (resultLeftChannelQueue.size() > 0 && resultRightChannelQueue.size() > 0) {
                ResultDataPortion l = resultLeftChannelQueue.peek();
                if (l.data.id() == idTodo) {
                    ResultDataPortion r = resultRightChannelQueue.peek();
                    if (r.data.id() == idTodo) {
                        resultLeftChannelQueue.remove();
                        resultRightChannelQueue.remove();

                        ConversionManagementInterface.ConversionResult cr = new ConversionManagementInterface.ConversionResult(
                                l.data, r.data, l.result, r.result
                        );
                        cm.receiver.result(cr);
                        ++idTodo;
                    }
                }
            }

            try {
                synchronized (this) {
                    wait(10);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    PriorityBlockingQueue<ResultDataPortion> resultLeftChannelQueue;
    PriorityBlockingQueue<ResultDataPortion> resultRightChannelQueue;
    ConversionManagement cm;
    int idTodo = 1;
}


public class ConversionManagement implements ConversionManagementInterface {
    private static int SEM_KILL_CORE = 100;
    private static int SEM_RES_MAX_AVAILABLE = 10000;

//    PMO_Log log = new PMO_Log();


    ConversionManagement() {
//        System.out.println("CM::ConversionManagement()");
        try {
            killCores.acquire(ConversionManagement.SEM_KILL_CORE);
//            killCores.acquire(ConversionManagement.SEM_RES_MAX_AVAILABLE);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        resultReceiver = new ResultReceiver(resultLeftChannelQueue, resultRightChannelQueue, this);
        resultReceiver.start();
    }

    @Override
    public void setCores(int cores) {
//        System.out.println("CM::setCores(" + cores + ")");

        int newCores = cores - maxCores;
        this.maxCores = cores;

        if (newCores < 0) {
            killCores.release(Math.abs(newCores));
        } else {
            for (int i = 0; i < newCores; ++i) {
                Thread th = new Converter(
                        this,
                        dataQueue,
                        killCores,
                        resultLeftChannelQueue,
                        resultRightChannelQueue);
                th.start();
            }
        }
    }

    @Override
    public void setConverter(ConverterInterface converter) {
//        System.out.println("CM::setConverter()");
        this.converter = converter;
    }

    @Override
    public void setConversionReceiver(ConversionReceiverInterface receiver) {
//        System.out.println("CM::setConversionReceiver()");
        this.receiver = receiver;
    }

    @Override
    public void addDataPortion(ConverterInterface.DataPortionInterface data) {
//        System.out.println("CM::addDataPortion(" + data.id() + "(" + data.channel() + ")" + ")");

        dataQueue.add(new DataPortion(data));
    }

    ConverterInterface converter;
    ConversionReceiverInterface receiver;

    ResultReceiver resultReceiver;

    int maxCores = 0;
    Semaphore killCores = new Semaphore(ConversionManagement.SEM_KILL_CORE);
    PriorityBlockingQueue<DataPortion> dataQueue = new PriorityBlockingQueue<>();
    PriorityBlockingQueue<ResultDataPortion> resultLeftChannelQueue = new PriorityBlockingQueue<>();
    PriorityBlockingQueue<ResultDataPortion> resultRightChannelQueue = new PriorityBlockingQueue<>();

}