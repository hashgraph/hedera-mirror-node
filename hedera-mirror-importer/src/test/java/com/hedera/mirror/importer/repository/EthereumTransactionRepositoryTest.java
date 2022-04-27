package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.converter.ByteArrayWeiBarConverter;
import com.hedera.mirror.common.converter.LongWeiBarConverter;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;

class EthereumTransactionRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private EthereumTransactionRepository ethereumTransactionRepository;

    @Test
    void save() {
        EthereumTransaction ethereumTransaction = domainBuilder.ethereumTransaction().persist();
        assertThat(ethereumTransactionRepository.findById(ethereumTransaction.getId()))
                .get()
                .isNotNull()
                .satisfies(t -> assertThat(t.getCallDataId().getId()).isEqualTo(ethereumTransaction.getCallDataId()
                        .getId()))
                .satisfies(t -> assertThat(t.getGasLimit()).isEqualTo(LongWeiBarConverter.INSTANCE.convertToDatabaseColumn(ethereumTransaction.getGasLimit())))
                .satisfies(t -> assertThat(t.getGasPrice()).isEqualTo(ByteArrayWeiBarConverter.INSTANCE.convertToDatabaseColumn(ethereumTransaction.getGasPrice())))
                .satisfies(t -> assertThat(t.getMaxFeePerGas()).isEqualTo(ByteArrayWeiBarConverter.INSTANCE.convertToDatabaseColumn(ethereumTransaction.getMaxFeePerGas())))
                .satisfies(t -> assertThat(t.getMaxGasAllowance()).isEqualTo(LongWeiBarConverter.INSTANCE.convertToDatabaseColumn(ethereumTransaction.getMaxGasAllowance())))
                .satisfies(t -> assertThat(t.getMaxPriorityFeePerGas()).isEqualTo(ByteArrayWeiBarConverter.INSTANCE.convertToDatabaseColumn(ethereumTransaction.getMaxPriorityFeePerGas())))
                .usingRecursiveComparison()
                .ignoringFields("callDataId", "gasLimit", "gasPrice", "maxFeePerGas", "maxGasAllowance",
                        "maxPriorityFeePerGas")
                .isEqualTo(ethereumTransaction);
    }
}
