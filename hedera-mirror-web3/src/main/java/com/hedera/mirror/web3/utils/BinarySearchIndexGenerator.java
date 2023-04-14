/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.utils;

import lombok.NonNull;

/** Generator of binary search probe values, given initial `[low, high]` range */
public class BinarySearchIndexGenerator {

    /** Lets caller know if we've reached the end of the search, or not */
    public enum State {
        NEXT,
        FINAL
    }

    /** Returned to caller as the next generated value for the binary search */
    public record Next(State state, long value) {}

    protected BinarySearchIndexGenerator(final long low, final long high) {
        if (high < low) throw new IllegalArgumentException("high < low");

        this.initialLow = low;
        this.initialHigh = high;

        this.currentLow = Math.max(this.initialLow, MIN_SENTINEL + 1);
        this.currentGuess = MIN_SENTINEL;
        this.currentHigh = this.initialHigh;
    }

    /** Caller informs `next` whether the last probe was good or not */
    public enum Last {
        TOO_LOW,
        TOO_HIGH
    }

    /** Search starts at initialLow and works its way up to find first success.
     *<p>
     * (First call: doesn't matter which `Last` you supply)
     *<p>
     * TODO: For our particular use case, searching for minimum gas, we should march up to the high limit
     * faster than binary search.  Reason is: It may be that _no_ amount of gas satisfies the contract.
     * In that case you'd like to _not_ have maximum number of iterations before giving up.  (Coming
     * back down the ordinary binary search would be fine.)
     */
    @NonNull
    public Next next(@NonNull final Last last) {
        if (MIN_SENTINEL == currentGuess) {
            // First probe
            currentGuess = currentLow;
            return new Next(State.NEXT, currentGuess);
        }

        switch (last) {
            case TOO_LOW -> currentLow = currentGuess;
            case TOO_HIGH -> currentHigh = currentGuess;
        }

        if (1 >= Math.abs(currentHigh - currentLow)) return new Next(State.FINAL, currentHigh);
        currentGuess = (currentHigh + currentLow) / 2;
        return new Next(State.NEXT, currentGuess);
    }

    private static final long MIN_SENTINEL = Long.MIN_VALUE;

    public final long initialLow;
    public final long initialHigh;

    private long currentLow;
    private long currentGuess;
    private long currentHigh;
}
