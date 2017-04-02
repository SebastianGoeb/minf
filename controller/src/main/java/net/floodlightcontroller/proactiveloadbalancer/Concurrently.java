package net.floodlightcontroller.proactiveloadbalancer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.emptyMap;

class Concurrently {

    static <X, T> Map<X, T> defaultResult() {
        return emptyMap();
    }

    static <X> void forEach(Collection<X> xs, Consumer<X> consumer) {
        if (xs == null || xs.isEmpty() || consumer == null) {
            return;
        }

        Map<X, ConsumerThread> threads = setupConsumerThreads(xs, consumer);
        startThreads(threads);
        waitForThreads(threads);
    }

    static <X, T> Map<X, T> forEach(Collection<X> xs, Function<X, T> function) {
        if (xs == null || xs.isEmpty() || function == null) {
            return defaultResult();
        }

        Map<X, FunctionThread<X, T>> threads = setupFunctionThreads(xs, function);
        startThreads(threads);
        waitForThreads(threads);

        // Collect results
        Map<X, T> results = new HashMap<>();
        for (Entry<X, FunctionThread<X, T>> entry : threads.entrySet()) {
            results.put(entry.getKey(), entry.getValue().getResult());
        }
        return results;
    }

    private static <X> Map<X, ConsumerThread> setupConsumerThreads(Collection<X> xs, Consumer<X> consumer) {
        Map<X, ConsumerThread> threads = new HashMap<>();
        for (X x : xs) {
            threads.put(x, new ConsumerThread<>(x, consumer));
        }
        return threads;
    }

    private static <X, T> Map<X, FunctionThread<X, T>> setupFunctionThreads(Collection<X> xs, Function<X, T> function) {
        Map<X, FunctionThread<X, T>> threads = new HashMap<>();
        for (X x : xs) {
            threads.put(x, new FunctionThread<>(x, function));
        }
        return threads;
    }

    private static void startThreads(Map<?, ? extends Thread> threads) {
        threads.values().forEach(Thread::start);
    }

    private static void waitForThreads(Map<?, ? extends Thread> threads) {
        for (Thread thread : threads.values()) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public static class ConsumerThread<X> extends Thread {
        private X x;
        private Consumer<X> consumer;

        private ConsumerThread(X x, Consumer<X> consumer) {
            super();
            this.x = x;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            consumer.accept(x);
        }
    }

    public static class FunctionThread<X, T> extends Thread {
        private T result;
        private X x;
        private Function<X, T> function;

        private FunctionThread(X x, Function<X, T> function) {
            super();
            this.x = x;
            this.function = function;
        }

        @Override
        public void run() {
            result = function.apply(x);
        }

        public T getResult() {
            return result;
        }
    }
}
