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

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import java.math.BigInteger;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.exception.InvalidEthereumBytesException;

public class Eip1559EthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {

    public static final String LONDON_RAW_TX =
            "02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";

    public static final String LONDON_PK = "033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d";

    @BeforeAll
    static void beforeAll() {
        ethereumTransactionParser = new Eip1559EthereumTransactionParser();
    }

    @SneakyThrows
    @Override
    public byte[] getTransactionBytes() {
        return Hex.decodeHex(LONDON_RAW_TX);
    }

    @Test
    void decodeLegacyType() {
        var ethereumTransactionBytes = RLPEncoder.encodeSequentially(
                Integers.toBytes(1),
                new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP1559 ethereum transaction bytes, 1st byte was 1 but should be 2");
    }

    @Test
    void decodeNonListRlpItem() {
        var ethereumTransactionBytes = RLPEncoder.encodeSequentially(
                Integers.toBytes(2),
                Integers.toBytes(1));

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP1559 ethereum transaction bytes, 2nd RLPItem was not a list");
    }

    @Test
    void decodeIncorrectRlpItemListSize() {
        var ethereumTransactionBytes = RLPEncoder.encodeSequentially(
                Integers.toBytes(2),
                new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP1559 ethereum transaction bytes, 2nd RLPItem list size was 0 but " +
                        "should be 12");
    }

    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull()
                .satisfies(t -> assertThat(t.getType()).isEqualTo(2))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getChainId())).isEqualTo("012a"))
                .satisfies(t -> assertThat(t.getNonce()).isEqualTo(2))
                .satisfies(t -> assertThat(t.getGasPrice()).isNull())
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getMaxPriorityFeePerGas())).isEqualTo("2f"))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getMaxFeePerGas())).isEqualTo("2f"))
                .satisfies(t -> assertThat(t.getGasLimit()).isEqualTo(98_304L))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getToAddress())).isEqualTo(
                        "7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181"))
                .satisfies(t -> assertThat(t.getValue()).isEqualTo(new BigInteger("0de0b6b3a7640000", 16).toByteArray()))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getCallData())).isEqualTo("123456"))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getAccessList())).isEmpty())
                .satisfies(t -> assertThat(t.getRecoveryId()).isEqualTo(1))
                .satisfies(t -> assertThat(t.getSignatureV()).isNull())
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureR())).isEqualTo(
                        "df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479"))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureS())).isEqualTo(
                        "1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66"));
    }
}
