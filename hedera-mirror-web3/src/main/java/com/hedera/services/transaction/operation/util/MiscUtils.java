package com.hedera.services.transaction.operation.util;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.BiConsumer;

public final class MiscUtils {
    public static <K, V extends MerkleNode & Keyed<K>> void forEach(
            final MerkleMap<K, V> map, final BiConsumer<? super K, ? super V> action) {
        map.forEachNode(
                (final MerkleNode node) -> {
                    if (node instanceof Keyed) {
                        final V leaf = node.cast();
                        action.accept(leaf.getKey(), leaf);
                    }
                });
    }

    /**
     * A permutation (invertible function) on 64 bits. The constants were found by automated search,
     * to optimize avalanche. Avalanche means that for a random number x, flipping bit i of x has
     * about a 50 percent chance of flipping bit j of perm64(x). For each possible pair (i,j), this
     * function achieves a probability between 49.8 and 50.2 percent.
     *
     * @param x the value to permute
     * @return the avalanche-optimized permutation
     */
    public static long perm64(long x) {
        // Shifts: {30, 27, 16, 20, 5, 18, 10, 24, 30}
        x += x << 30;
        x ^= x >>> 27;
        x += x << 16;
        x ^= x >>> 20;
        x += x << 5;
        x ^= x >>> 18;
        x += x << 10;
        x ^= x >>> 24;
        x += x << 30;
        return x;
    }
}
