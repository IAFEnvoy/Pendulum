package iafenvoy.pendulum.interpreter.util;

import java.util.LinkedList;

public class TaskQueue<E> extends LinkedList<E> {
    private Callback<E> callback;
    private final Thread workingThread = new Thread(() -> {
        while (true) {
            if (this.isEmpty()) {
                this.pauseThread();
                continue;
            }
            E element = this.poll();
            if (this.callback != null)
                this.callback.execute(element);
            else
                throw new IllegalArgumentException("Unexpected null callback");
        }
    });

    public TaskQueue(Callback<E> callback) {
        super();
        this.callback = callback;
        this.workingThread.setName("Pendulum interpreter");
        workingThread.start();
    }

    @Override
    public boolean offer(E e) {
        boolean isStart = !this.isEmpty();
        boolean ret = super.offer(e);
        if (!isStart) {
            workingThread.resume();
        }
        return ret;
    }

    private void pauseThread() {
        workingThread.suspend();
    }

    public interface Callback<T> {
        void execute(T element);
    }
}
