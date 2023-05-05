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

package com.hedera.services.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class BitPackUtils {
    private static final int MAX_AUTOMATIC_ASSOCIATIONS_MASK = (1 << 16) - 1;
    private static final int ALREADY_USED_AUTOMATIC_ASSOCIATIONS_MASK = MAX_AUTOMATIC_ASSOCIATIONS_MASK << 16;

    public static final long MAX_NUM_ALLOWED = 0xFFFFFFFFL;
    private static final long MASK_INT_AS_UNSIGNED_LONG = (1L << 32) - 1;

    /**
     * Returns the positive long represented by the given integer code.
     *
     * @param code an int to interpret as unsigned
     * @return the corresponding positive long
     */
    public static long numFromCode(int code) {
        return code & MASK_INT_AS_UNSIGNED_LONG;
    }

    /**
     * Returns the int representing the given positive long.
     *
     * @param num a positive long
     * @return the corresponding integer code
     */
    public static int codeFromNum(long num) {
        assertValid(num);
        return (int) num;
    }

    /**
     * Throws an exception if the given long is not a number in the allowed range.
     *
     * @param num the long to check
     * @throws IllegalArgumentException if the argument is less than 0 or greater than 4_294_967_295
     */
    public static void assertValid(long num) {
        if (num < 0 || num > MAX_NUM_ALLOWED) {
            throw new IllegalArgumentException("Serial number " + num + " out of range!");
        }
    }

    /**
     * Checks if the given long is not a number in the allowed range
     *
     * @param num given long number to check
     * @return true if valid, else returns false
     */
    public static boolean isValidNum(long num) {
        return num >= 0 && num <= MAX_NUM_ALLOWED;
    }
    /**
     * Returns the lower-order 16 bits of a given {@code int}
     *
     * @param autoAssociationMetadata any int
     * @return the low-order 16-bits
     */
    public static int getMaxAutomaticAssociationsFrom(int autoAssociationMetadata) {
        return autoAssociationMetadata & MAX_AUTOMATIC_ASSOCIATIONS_MASK;
    }

    /**
     * Returns the higher-order 16 bits of a given {@code int}
     *
     * @param autoAssociationMetadata any int
     * @return the higher-order 16-bits
     */
    public static int getAlreadyUsedAutomaticAssociationsFrom(int autoAssociationMetadata) {
        return (autoAssociationMetadata & ALREADY_USED_AUTOMATIC_ASSOCIATIONS_MASK) >>> 16;
    }
}
