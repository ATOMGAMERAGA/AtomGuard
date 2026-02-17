package com.atomguard.util;

import com.github.retrooper.packetevents.protocol.nbt.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Iterative NBT Scanner for deep-level validation.
 * Uses PacketEvents NBT API to scan the actual NBT tree without recursion to avoid StackOverflowError.
 *
 * @author AtomGuard Team
 * @version 1.0.0
 */
public class RecursiveNBTScanner {

    private static final int MAX_ITERATIONS = 100000;

    /**
     * Scans an NBT compound iteratively to check for depth, tag count, and size.
     *
     * @param compound The NBT compound to scan
     * @param maxDepth Maximum allowed depth
     * @param maxTags Maximum allowed total tag count
     * @param maxSizeBytes Maximum allowed total size in bytes
     * @return true if safe, false if limits exceeded
     */
    public static boolean isSafe(@Nullable NBTCompound compound, int maxDepth, int maxTags, int maxSizeBytes) {
        if (compound == null) return true;

        Deque<NBTNode> stack = new ArrayDeque<>();
        stack.push(new NBTNode(compound, 1));

        int tagCount = 0;
        int sizeBytes = 0;
        int iterations = 0;

        while (!stack.isEmpty()) {
            iterations++;
            if (iterations > MAX_ITERATIONS) return false;

            NBTNode currentNode = stack.pop();
            NBT currentTag = currentNode.tag;
            int currentDepth = currentNode.depth;

            tagCount++;
            sizeBytes += estimateTagSize(currentTag);

            if (tagCount > maxTags || sizeBytes > maxSizeBytes || currentDepth > maxDepth) {
                return false;
            }

            if (currentTag instanceof NBTCompound c) {
                for (Map.Entry<String, NBT> entry : c.getTags().entrySet()) {
                    // Key size calculation
                    sizeBytes += entry.getKey().length() * 2;
                    stack.push(new NBTNode(entry.getValue(), currentDepth + 1));
                }
            } else if (currentTag instanceof NBTList<?> list) {
                for (Object elementObj : list.getTags()) {
                    if (elementObj instanceof NBT element) {
                        stack.push(new NBTNode(element, currentDepth + 1));
                    }
                }
            }
        }

        return true;
    }

    /**
     * Estimates the size of a single tag (shallow).
     */
    private static int estimateTagSize(NBT tag) {
        if (tag instanceof NBTByte) return 1;
        if (tag instanceof NBTShort) return 2;
        if (tag instanceof NBTInt) return 4;
        if (tag instanceof NBTLong) return 8;
        if (tag instanceof NBTFloat) return 4;
        if (tag instanceof NBTDouble) return 8;
        if (tag instanceof NBTByteArray ba) return ba.getValue().length;
        if (tag instanceof NBTIntArray ia) return ia.getValue().length * 4;
        if (tag instanceof NBTLongArray la) return la.getValue().length * 8;
        if (tag instanceof NBTString s) return s.getValue().length() * 2;
        return 0; // Compound/List overhead is negligible or counted via children
    }

    // Helper record for stack
    private record NBTNode(NBT tag, int depth) {}

    // Backward compatibility method
    public static boolean isSafe(@Nullable NBTCompound compound, int maxDepth, int maxTags) {
        return isSafe(compound, maxDepth, maxTags, Integer.MAX_VALUE);
    }
}