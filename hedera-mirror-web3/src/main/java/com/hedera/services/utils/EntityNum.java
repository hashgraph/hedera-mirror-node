package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.services.utils.BitPackUtils.codeFromNum;
import static com.hedera.services.utils.BitPackUtils.isValidNum;
import static com.hedera.services.utils.BitPackUtils.numFromCode;
import static com.hedera.services.utils.BitPackUtils.perm64;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An integer whose {@code hashCode()} implementation vastly reduces the risk of hash collisions in structured data
 * using this type, when compared to the {@code java.lang.Integer} boxed type.
 */
public class EntityNum implements Comparable<EntityNum> {
    public static final EntityNum MISSING_NUM = new EntityNum(0);

    private final int value;

    public EntityNum(final int value) {
        this.value = value;
    }

    public static EntityNum fromInt(final int i) {
        return new EntityNum(i);
    }

    public static EntityNum fromLong(final long l) {
        if (!isValidNum(l)) {
            return MISSING_NUM;
        }
        final var value = codeFromNum(l);
        return new EntityNum(value);
    }

    public int intValue() {
        return value;
    }

    public long longValue() {
        return numFromCode(value);
    }

    @Override
    public int hashCode() {
        return (int) perm64(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || EntityNum.class != o.getClass()) {
            return false;
        }

        final var that = (EntityNum) o;

        return this.value == that.value;
    }

    @Override
    public String toString() {
        return "EntityNum{" + "value=" + value + '}';
    }

    @Override
    public int compareTo(@NonNull final EntityNum that) {
        return Integer.compare(this.value, that.value);
    }
}
