/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.transaction.operation.helpers;

import com.google.common.primitives.Longs;

import com.hedera.services.transaction.operation.util.MiscUtils;

import org.jetbrains.annotations.NotNull;

public class EntityNum implements Comparable<EntityNum> {
    private final int value;

    public EntityNum(int value) {
        this.value = value;
    }

    @Override
    public int hashCode() {
        return (int) MiscUtils.perm64(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || EntityNum.class != o.getClass()) {
            return false;
        }

        var that = (EntityNum) o;

        return this.value == that.value;
    }

    @Override
    public String toString() {
        return "EntityNum{" + "value=" + value + '}';
    }

    @Override
    public int compareTo(@NotNull final EntityNum that) {
        return Integer.compare(this.value, that.value);
    }
}
