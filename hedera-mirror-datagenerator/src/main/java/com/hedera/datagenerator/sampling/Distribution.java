package com.hedera.datagenerator.sampling;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public interface Distribution<T> {
    /**
     * @return Returns an item sampled from the distribution.
     */
    T sample();

    /**
     * @return Returns collection of {@code N} items sampled from the distribution.
     */
    default List<T> sample(int n) {
        List<T> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(sample());
        }
        return result;
    }

    /**
     * Be careful when using this function to sample distinct values, if the sampling domain is not big enough, it'll go
     * into infinite loop.
     *
     * @return collection of {@code N} distinct items sampled from the distribution.
     */
    default List<T> sampleDistinct(int n) {
        Set<T> result = new HashSet<>(n);
        for (int i = 0; i < n; ) {
            T newElement = sample();
            if (!result.contains(newElement)) {
                result.add(newElement);
                i++;
            }
        }
        return new ArrayList<>(result);
    }
}
