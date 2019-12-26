package com.hedera.faker.domain.generators.transaction;
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

import java.util.Map;
import javax.inject.Named;

import com.hedera.faker.common.TransactionGenerator;
import com.hedera.faker.common.TransactionGeneratorProperties;
import com.hedera.faker.sampling.FrequencyDistribution;

/**
 * Generates mixed types of transactions. Uses CryptoTransactionGenerator and FileTransactionGenerator.
 */
@Named
public class DomainTransactionGenerator implements TransactionGenerator {

    private final FrequencyDistribution<TransactionGenerator> mixedTransactionGenerator;

    public DomainTransactionGenerator(
            TransactionGeneratorProperties properties, CryptoTransactionGenerator cryptoTransactionGenerator,
            FileTransactionGenerator fileTransactionGenerator) {
        mixedTransactionGenerator = new FrequencyDistribution<>(Map.of(
                cryptoTransactionGenerator, properties.getCryptoTransactionsPerThousand(),
                fileTransactionGenerator, properties.getFileTransactionsPerThousand()
        ));
    }

    @Override
    public void generateTransaction(long consensusTimestampNs) {
        mixedTransactionGenerator.sample().generateTransaction(consensusTimestampNs);
    }
}
