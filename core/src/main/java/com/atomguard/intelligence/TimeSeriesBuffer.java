package com.atomguard.intelligence;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe circular buffer for time series data.
 * Constant-size memory footprint, O(1) add, O(n) statistics.
 *
 * @author AtomGuard Team
 * @version 1.2.0
 */
public class TimeSeriesBuffer {

    private final double[] data;
    private final int capacity;
    private int head = 0;
    private int size = 0;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public TimeSeriesBuffer(int capacity) {
        this.capacity = capacity;
        this.data = new double[capacity];
    }

    public void add(double value) {
        lock.writeLock().lock();
        try {
            data[head] = value;
            head = (head + 1) % capacity;
            if (size < capacity) size++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public double getMean() {
        lock.readLock().lock();
        try {
            if (size == 0) return 0;
            double sum = 0;
            for (int i = 0; i < size; i++) sum += data[i];
            return sum / size;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getStdDev() {
        lock.readLock().lock();
        try {
            if (size < 2) return 1.0; // Bölme sıfıra gitmesini önle
            double mean = computeMeanInternal();
            double variance = 0;
            for (int i = 0; i < size; i++) {
                double diff = data[i] - mean;
                variance += diff * diff;
            }
            double std = Math.sqrt(variance / size);
            return std < 0.001 ? 1.0 : std; // Minimum stddev
        } finally {
            lock.readLock().unlock();
        }
    }

    private double computeMeanInternal() {
        if (size == 0) return 0;
        double sum = 0;
        for (int i = 0; i < size; i++) sum += data[i];
        return sum / size;
    }

    public double getMin() {
        lock.readLock().lock();
        try {
            if (size == 0) return 0;
            double min = data[0];
            for (int i = 1; i < size; i++) if (data[i] < min) min = data[i];
            return min;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getMax() {
        lock.readLock().lock();
        try {
            if (size == 0) return 0;
            double max = data[0];
            for (int i = 1; i < size; i++) if (data[i] > max) max = data[i];
            return max;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getLast() {
        lock.readLock().lock();
        try {
            if (size == 0) return 0;
            return data[(head - 1 + capacity) % capacity];
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getPercentile(double p) {
        lock.readLock().lock();
        try {
            if (size == 0) return 0;
            double[] sorted = Arrays.copyOfRange(data, 0, size);
            Arrays.sort(sorted);
            int index = (int) Math.ceil(p / 100.0 * size) - 1;
            return sorted[Math.max(0, Math.min(index, size - 1))];
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSize() { return size; }
    public int getCapacity() { return capacity; }
    public boolean isFull() { return size == capacity; }
}
