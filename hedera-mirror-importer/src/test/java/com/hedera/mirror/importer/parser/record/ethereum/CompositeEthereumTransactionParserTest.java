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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

public class CompositeEthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {

    public static final String LONDON_RAW_TX =
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";

    static final String LESS_THAN_ERROR_MESSAGE = "Ethereum transaction bytes length is less than 2 bytes in length";

    @BeforeAll
    static void beforeAll() {
        ethereumTransactionParser = new CompositeEthereumTransactionParser(new LegacyEthereumTransactionParser(),
                new Eip1559EthereumTransactionParser());
    }

    @SneakyThrows
    @Override
    public byte[] getTransactionBytes() {
        return Hex.decodeHex(LONDON_RAW_TX);
    }

    @Test
    public void decodeNullBytes() {
        assertThatThrownBy(() -> ethereumTransactionParser.decode(null))
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessage(LESS_THAN_ERROR_MESSAGE);
    }

    @Test
    public void decodeEmptyBytes() {
        assertThatThrownBy(() -> ethereumTransactionParser.decode(new byte[0]))
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessage(LESS_THAN_ERROR_MESSAGE);
    }

    @Test
    public void decodeLessThanMinByteSize() {
        assertThatThrownBy(() -> ethereumTransactionParser.decode(new byte[] {1}))
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessage(LESS_THAN_ERROR_MESSAGE);
    }

    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull();
    }
}
