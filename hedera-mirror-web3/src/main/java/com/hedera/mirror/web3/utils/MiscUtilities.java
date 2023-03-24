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

public class MiscUtilities {

    /** Do a bunch of `Objects.requireNonNull` checks in one go.
     *
     * @param args array of even length, odd entries are values to be checked against null, even entries are
     *             the names of the preceeding odd entry (names must be Strings and must not be null)
     */
    public static void requireAllNonNull(final Object... args) {
        if (1 == args.length % 2)
            throw new IllegalArgumentException("odd number of arguments presented, even number required");
        for (int i = 0; i < args.length; i += 2)
            if (!(args[i + 1] instanceof String))
                throw new IllegalArgumentException(
                        "even numbered arguments (0-based of course) must be non-null and of type String");
        final var sb = new StringBuilder(20);
        for (int i = 0; i < args.length; i += 2)
            if (null == args[i]) {
                sb.append("argument %s must not be null; ".formatted(args[i + 1]));
            }
        if (!sb.isEmpty()) {
            sb.setLength(sb.length() - 2);
            throw new NullPointerException(sb.toString());
        }
    }

    private MiscUtilities() {
        throw new UnsupportedOperationException("should never instantiate utility class MiscUtilities");
    }
}
