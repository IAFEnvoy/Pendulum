package iafenvoy.pendulum.interpreter.util;

import java.util.LinkedList;

public class TaskQueue<E> {
    private final Callback<E> callback;
    private final LinkedList<E> tasks = new LinkedList<>();
    private final Thread workingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    if (tasks.isEmpty())
                        synchronized (tasks) {
                            tasks.wait();
                        }
                    E element = tasks.poll();
                    if (callback != null)
                        callback.execute(element);
                    else
                        throw new IllegalArgumentException("Unexpected null callback");
                }
            } catch (Exception e) {
                System.out.println("The working thread crash!");
                e.printStackTrace();
            }
        }
    });

    public TaskQueue(Callback<E> callback, String threadName) {
        this.callback = callback;
        this.workingThread.setName(threadName);
        workingThread.start();
    }

    public void offer(E e) {
        this.tasks.offer(e);
        synchronized (tasks) {
            tasks.notifyAll();
        }
    }

    public interface Callback<T> {
        void execute(T element);
    }
}
