/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.ethereum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.exception.InvalidEthereumBytesException;
import java.math.BigInteger;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Eip2930EthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {
    public static final String EIP_2930_RAW_TX =
            "01f86e82012a8085a54f4c3c00832dc6c094000000000000000000000000000000000000052d8502540be40080c001a0c85ee8f8f08ac4363dad9185d1798af1252745432298299498b4588de66b3efba005e8e49e8507d42b4147a050e1c5deaf7eac3dc5dc0f51e57fe21a3cbb7624ac";

    @BeforeAll
    static void beforeAll() {
        ethereumTransactionParser = new Eip2930EthereumTransactionParser();
    }

    @SneakyThrows
    @Override
    public byte[] getTransactionBytes() {
        return Hex.decodeHex(EIP_2930_RAW_TX);
    }

    @SneakyThrows
    @Test
    void decodeEip2930Transaction() {
        var ethereumTransaction = ethereumTransactionParser.decode(Hex.decodeHex(EIP_2930_RAW_TX));
        validateEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction.getType()).isEqualTo(1);
    }

    @Test
    void decodeEip1559Type() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(2), new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP2930 ethereum transaction bytes, 1st byte was 2 but should be 1");
    }

    @Test
    void decodeNonListRlpItem() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(1), Integers.toBytes(1));

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP2930 ethereum transaction bytes, 2nd RLPItem was not a list");
    }

    @Test
    void decodeIncorrectRlpItemListSize() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(1), new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP2930 ethereum transaction bytes, 2nd RLPItem list size was 0 but "
                        + "should be 11");
    }

    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull()
                .satisfies(t -> assertThat(t.getType()).isEqualTo(Eip2930EthereumTransactionParser.EIP2930_TYPE_BYTE))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getChainId())).isEqualTo("012a"))
                .satisfies(t -> assertThat(t.getNonce()).isEqualTo(0L))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getGasPrice())).isEqualTo("a54f4c3c00"))
                .satisfies(t -> assertThat(t.getMaxPriorityFeePerGas()).isNull())
                .satisfies(t -> assertThat(t.getMaxFeePerGas()).isNull())
                .satisfies(t -> assertThat(t.getGasLimit()).isEqualTo(3_000_000L))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getToAddress()))
                        .isEqualTo("000000000000000000000000000000000000052d"))
                .satisfies(t -> assertThat(t.getValue()).isEqualTo(new BigInteger("2540be400", 16).toByteArray()))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getCallData())).isEmpty())
                .satisfies(
                        t -> assertThat(Hex.encodeHexString(t.getAccessList())).isEmpty())
                .satisfies(t -> assertThat(t.getRecoveryId()).isEqualTo(1))
                .satisfies(t -> assertThat(t.getSignatureV()).isNull())
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureR()))
                        .isEqualTo("c85ee8f8f08ac4363dad9185d1798af1252745432298299498b4588de66b3efb"))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureS()))
                        .isEqualTo("05e8e49e8507d42b4147a050e1c5deaf7eac3dc5dc0f51e57fe21a3cbb7624ac"));
    }
}
