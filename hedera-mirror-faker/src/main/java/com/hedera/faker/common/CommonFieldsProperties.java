package com.hedera.faker.common;
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import lombok.Data;

import com.hedera.faker.sampling.NumberDistributionConfig;

@Data
class CommonFieldsProperties {
    final NumberDistributionConfig memoSizeBytes = new NumberDistributionConfig();

//        final NumberDistributionConfig chargedTxnFee = new NumberDistributionConfig(100000);

//        final NumberDistributionConfig maxFee = new NumberDistributionConfig(1000000);

//        final NumberDistributionConfig initialBalance = new NumberDistributionConfig(0);

//        final NumberDistributionConfig validDurationSec = new NumberDistributionConfig(120);

    void initDistributions() {
        memoSizeBytes.initDistribution();
    }
}
