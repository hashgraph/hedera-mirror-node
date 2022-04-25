package com.hedera.mirror.importer.parser.record.ethereum;

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
import static org.junit.jupiter.api.Assertions.*;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

abstract class AbstractEthereumTransactionParserTest {
    protected static EthereumTransactionParser ethereumTransactionParser;
    @Resource
    private final DomainBuilder domainBuilder = new DomainBuilder();

    protected abstract byte[] getTransactionBytes();

    protected abstract void validateEthereumTransaction(EthereumTransaction ethereumTransaction);

    @Test
    public void parse() {
        var ethereumTransaction = ethereumTransactionParser.parse(getTransactionBytes());
        assertThat(ethereumTransaction)
                .isNotNull()
                .satisfies(t -> assertThat(t.getChainId()).isNotEmpty());

        validateEthereumTransaction(ethereumTransaction);
    }

    @Test
    public void parseInvalidBytes() {
        var ethereumTransaction = domainBuilder.ethereumTransaction()
                .get();
        ethereumTransaction.setSignatureR(new byte[0]);

        assertThrows(InvalidDatasetException.class,
                () -> ethereumTransactionParser.retrievePublicKey(ethereumTransaction));
    }
}
