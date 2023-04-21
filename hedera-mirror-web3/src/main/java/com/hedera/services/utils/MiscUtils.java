/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.utils;

import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;
import java.util.Optional;

public final class MiscUtils {

    private MiscUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * A permutation (invertible function) on 64 bits. The constants were found by automated search, to optimize
     * avalanche. Avalanche means that for a random number x, flipping bit i of x has about a 50 percent chance of
     * flipping bit j of perm64(x). For each possible pair (i,j), this function achieves a probability between 49.8 and
     * 50.2 percent.
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


    public static Optional<JKey> asUsableFcKey(final Key key) {
        try {
            final var fcKey = JKey.mapKey(key);
            if (!fcKey.isValid()) {
                return Optional.empty();
            }
            return Optional.of(fcKey);
        } catch (final DecoderException ignore) {
            return Optional.empty();
        }
    }
}
