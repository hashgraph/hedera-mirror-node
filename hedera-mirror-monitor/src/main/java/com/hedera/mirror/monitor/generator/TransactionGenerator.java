package com.hedera.mirror.monitor.generator;

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

import com.hedera.mirror.monitor.publish.PublishRequest;

import java.util.List;

public interface TransactionGenerator {

    /**
     * Gets the next count publish requests. If count > 0, up to count publish requests will be generated;
     * if count <= 0, the generator will determine the actual count.
     *
     * @param count
     * @return
     */
    List<PublishRequest> next(long count);

    default List<PublishRequest> next() {
        return next(1);
    }
}
