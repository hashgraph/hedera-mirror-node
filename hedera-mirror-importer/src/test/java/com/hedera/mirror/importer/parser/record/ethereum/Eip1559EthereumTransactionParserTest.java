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

import java.math.BigInteger;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeAll;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;

public class Eip1559EthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {

    static final String LONDON_RAW_TX =
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";

    @BeforeAll
    static void beforeAll() {
        ethereumTransactionParser = new Eip1559EthereumTransactionParser();
    }

    @Override
    public byte[] getTransactionBytes() {
        return Hex.decode(LONDON_RAW_TX);
    }

    @Override
    public void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull()
                .satisfies(t -> assertThat(t.getType()).isEqualTo(2))
                .satisfies(t -> assertThat(Hex.toHexString(t.getChainId())).isEqualTo("012a"))
                .satisfies(t -> assertThat(t.getNonce()).isEqualTo(2))
                .satisfies(t -> assertThat(t.getGasPrice()).isNull())
                .satisfies(t -> assertThat(Hex.toHexString(t.getMaxPriorityFeePerGas())).isEqualTo("2f"))
                .satisfies(t -> assertThat(Hex.toHexString(t.getMaxFeePerGas())).isEqualTo("2f"))
                .satisfies(t -> assertThat(t.getGasLimit()).isEqualTo(98_304L))
                .satisfies(t -> assertThat(Hex.toHexString(t.getToAddress())).isEqualTo(
                        "7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181"))
                .satisfies(t -> assertThat(t.getValue()).isEqualTo(new BigInteger("0de0b6b3a7640000", 16).toByteArray()))
                .satisfies(t -> assertThat(Hex.toHexString(t.getCallData())).isEqualTo("123456"))
                .satisfies(t -> assertThat(Hex.toHexString(t.getAccessList())).isEmpty())
                .satisfies(t -> assertThat(t.getRecoveryId()).isEqualTo(1))
                .satisfies(t -> assertThat(t.getSignatureV()).isNull())
                .satisfies(t -> assertThat(Hex.toHexString(t.getSignatureR())).isEqualTo(
                        "df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479"))
                .satisfies(t -> assertThat(Hex.toHexString(t.getSignatureS())).isEqualTo(
                        "1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66"));
    }
}
