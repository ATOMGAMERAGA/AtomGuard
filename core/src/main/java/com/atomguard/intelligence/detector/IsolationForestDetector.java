package com.atomguard.intelligence.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Izolasyon Ormani tabanli anomali dedektoru.
 * Rastgele izolasyon agaclari ile ortalama yol uzunluguna dayali anomali skoru hesaplar.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class IsolationForestDetector {

    private final int numTrees;
    private final int sampleSize;
    private final double anomalyThreshold;
    private final int minSamples;
    private final int rebuildInterval;

    // Circular buffer for training data
    private final double[] buffer;
    private int bufferIndex = 0;
    private int bufferCount = 0;

    private long sampleCount = 0;
    private long lastBuildSample = 0;

    private List<IsolationTree> forest;
    private final Random random;

    public IsolationForestDetector(int numTrees, int sampleSize, double anomalyThreshold,
                                   int minSamples, int rebuildInterval) {
        this.numTrees = numTrees;
        this.sampleSize = sampleSize;
        this.anomalyThreshold = anomalyThreshold;
        this.minSamples = minSamples;
        this.rebuildInterval = rebuildInterval;
        this.buffer = new double[sampleSize * 4];
        this.random = new Random();
    }

    public synchronized boolean isAnomaly(double value) {
        addToBuffer(value);
        sampleCount++;

        if (sampleCount < minSamples) {
            return false;
        }

        if (forest == null || (sampleCount - lastBuildSample) >= rebuildInterval) {
            buildForest();
        }

        return getAnomalyScore(value) > anomalyThreshold;
    }

    public synchronized double getAnomalyScore(double value) {
        if (forest == null || forest.isEmpty()) {
            return 0.0;
        }

        double avgPath = 0;
        for (IsolationTree tree : forest) {
            avgPath += tree.pathLength(value);
        }
        avgPath /= forest.size();

        double c = averagePathLength(Math.min(bufferCount, sampleSize));
        return Math.pow(2.0, -avgPath / c);
    }

    public synchronized void reset() {
        bufferIndex = 0;
        bufferCount = 0;
        sampleCount = 0;
        lastBuildSample = 0;
        forest = null;
    }

    private void addToBuffer(double value) {
        buffer[bufferIndex] = value;
        bufferIndex = (bufferIndex + 1) % buffer.length;
        if (bufferCount < buffer.length) {
            bufferCount++;
        }
    }

    private void buildForest() {
        double[] data = getBufferData();
        if (data.length < 2) {
            return;
        }

        forest = new ArrayList<>(numTrees);
        int actualSampleSize = Math.min(sampleSize, data.length);

        for (int i = 0; i < numTrees; i++) {
            double[] sample = subsample(data, actualSampleSize);
            forest.add(new IsolationTree(sample, random));
        }

        lastBuildSample = sampleCount;
    }

    private double[] getBufferData() {
        double[] data = new double[bufferCount];
        for (int i = 0; i < bufferCount; i++) {
            int idx = (bufferIndex - bufferCount + i + buffer.length) % buffer.length;
            data[i] = buffer[idx];
        }
        return data;
    }

    private double[] subsample(double[] data, int size) {
        double[] sample = new double[size];
        // Fisher-Yates partial shuffle
        double[] copy = data.clone();
        for (int i = 0; i < size; i++) {
            int j = i + random.nextInt(copy.length - i);
            double tmp = copy[i];
            copy[i] = copy[j];
            copy[j] = tmp;
            sample[i] = copy[i];
        }
        return sample;
    }

    static double averagePathLength(int n) {
        if (n <= 1) {
            return 1.0;
        }
        double harmonic = Math.log(n - 1.0) + 0.5772156649;
        return 2.0 * harmonic - 2.0 * (n - 1.0) / n;
    }

    // Getters
    public long getSampleCount() {
        return sampleCount;
    }

    public double getAnomalyThreshold() {
        return anomalyThreshold;
    }

    public int getNumTrees() {
        return numTrees;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    // === Inner classes ===

    private static class IsolationTree {
        private final IsolationTreeNode root;
        private final int maxDepth;

        IsolationTree(double[] data, Random random) {
            this.maxDepth = (int) Math.ceil(Math.log(data.length) / Math.log(2));
            this.root = buildTree(data, 0, random);
        }

        double pathLength(double value) {
            return pathLength(value, root, 0);
        }

        private double pathLength(double value, IsolationTreeNode node, int depth) {
            if (node.isLeaf()) {
                return depth + averagePathLength(node.size);
            }
            if (value < node.splitValue) {
                return pathLength(value, node.left, depth + 1);
            } else {
                return pathLength(value, node.right, depth + 1);
            }
        }

        private IsolationTreeNode buildTree(double[] data, int depth, Random random) {
            if (data.length <= 1 || depth >= maxDepth) {
                return new IsolationTreeNode(data.length);
            }

            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (double v : data) {
                if (v < min) min = v;
                if (v > max) max = v;
            }

            if (min == max) {
                return new IsolationTreeNode(data.length);
            }

            double splitValue = min + random.nextDouble() * (max - min);

            // partition
            List<Double> leftList = new ArrayList<>();
            List<Double> rightList = new ArrayList<>();
            for (double v : data) {
                if (v < splitValue) {
                    leftList.add(v);
                } else {
                    rightList.add(v);
                }
            }

            // edge case: if one side is empty, make leaf
            if (leftList.isEmpty() || rightList.isEmpty()) {
                return new IsolationTreeNode(data.length);
            }

            IsolationTreeNode node = new IsolationTreeNode(splitValue);
            node.left = buildTree(toArray(leftList), depth + 1, random);
            node.right = buildTree(toArray(rightList), depth + 1, random);
            return node;
        }

        private double[] toArray(List<Double> list) {
            double[] arr = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = list.get(i);
            }
            return arr;
        }
    }

    private static class IsolationTreeNode {
        double splitValue;
        int size; // for leaf nodes
        IsolationTreeNode left;
        IsolationTreeNode right;

        // Internal node
        IsolationTreeNode(double splitValue) {
            this.splitValue = splitValue;
            this.size = 0;
        }

        // Leaf node
        IsolationTreeNode(int size) {
            this.size = size;
            this.splitValue = 0;
        }

        boolean isLeaf() {
            return left == null && right == null;
        }
    }
}
