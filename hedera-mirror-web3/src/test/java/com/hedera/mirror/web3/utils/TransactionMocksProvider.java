/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.TransactionMocks.getContractCallEthTransaction;
import static com.hedera.mirror.common.domain.TransactionMocks.getContractCallRecordFile;
import static com.hedera.mirror.common.domain.TransactionMocks.getContractCallTransaction;
import static com.hedera.mirror.common.domain.TransactionMocks.getCreateContractEthTransaction;
import static com.hedera.mirror.common.domain.TransactionMocks.getCreateContractRecordFile;
import static com.hedera.mirror.common.domain.TransactionMocks.getCreateContractTransaction;
import static com.hedera.mirror.common.domain.TransactionMocks.getEip1559EthTransaction;
import static com.hedera.mirror.common.domain.TransactionMocks.getEip1559RecordFile;
import static com.hedera.mirror.common.domain.TransactionMocks.getEip1559Transaction;
import static com.hedera.mirror.common.domain.TransactionMocks.getEip2930EthTransaction;
import static com.hedera.mirror.common.domain.TransactionMocks.getEip2930RecordFile;
import static com.hedera.mirror.common.domain.TransactionMocks.getEip2930Transaction;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.Transaction;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Provides mocked {@link Transaction}, {@link EthereumTransaction} and {@link RecordFile} for testing purposes.
 * Can be registered via the {@link ArgumentsSource} annotation.
 */
public class TransactionMocksProvider implements ArgumentsProvider {

    /**
     * Provides {@link Stream} of {@link Arguments} to be passed to a {@code @ParameterizedTest}.
     *
     * @param context the current extension context; never {@code null}
     * @return {@link Stream} of {@link Arguments}, containing mocked
     * {@link Transaction}, {@link EthereumTransaction} and {@link RecordFile}.
     */
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
        return Stream.of(
                Arguments.of(getCreateContractTransaction(), getCreateContractEthTransaction(), getCreateContractRecordFile()),
                Arguments.of(getContractCallTransaction(), getContractCallEthTransaction(), getContractCallRecordFile()),
                Arguments.of(getEip1559Transaction(), getEip1559EthTransaction(), getEip1559RecordFile()),
                Arguments.of(getEip2930Transaction(), getEip2930EthTransaction(), getEip2930RecordFile())
        );
    }
}