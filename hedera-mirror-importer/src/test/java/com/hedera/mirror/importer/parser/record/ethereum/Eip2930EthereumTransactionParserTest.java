/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Eip2930EthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {
    public static final String EIP_2930_RAW_TX =
            "01f87382012a82160c85a54f4c3c00832dc6c094000000000000000000000000000000000000052d8502540be40083123456c001a0abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816a0249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53";

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
                .hasMessage("Unable to decode EIP2930 ethereum transaction bytes, First byte was 2 but should be 1");
    }

    @Test
    void decodeNonListRlpItem() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(1), Integers.toBytes(1));

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP2930 ethereum transaction bytes, Second RLPItem was not a list");
    }

    @Test
    void decodeIncorrectRlpItemListSize() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(1), new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP2930 ethereum transaction bytes, RLP list size was 0 but expected 11");
    }

    @SneakyThrows
    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull()
                .returns(Eip2930EthereumTransactionParser.EIP2930_TYPE_BYTE, EthereumTransaction::getType)
                .returns(Hex.decodeHex("012a"), EthereumTransaction::getChainId)
                .returns(5644L, EthereumTransaction::getNonce)
                .returns(Hex.decodeHex("a54f4c3c00"), EthereumTransaction::getGasPrice)
                .returns(null, EthereumTransaction::getMaxPriorityFeePerGas)
                .returns(null, EthereumTransaction::getMaxFeePerGas)
                .returns(3_000_000L, EthereumTransaction::getGasLimit)
                .returns(Hex.decodeHex("000000000000000000000000000000000000052d"), EthereumTransaction::getToAddress)
                .returns(Hex.decodeHex("02540be400"), EthereumTransaction::getValue)
                .returns(Hex.decodeHex("123456"), EthereumTransaction::getCallData)
                .returns(Hex.decodeHex(""), EthereumTransaction::getAccessList)
                .returns(1, EthereumTransaction::getRecoveryId)
                .returns(null, EthereumTransaction::getSignatureV)
                .returns(
                        Hex.decodeHex("abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816"),
                        EthereumTransaction::getSignatureR)
                .returns(
                        Hex.decodeHex("249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53"),
                        EthereumTransaction::getSignatureS);
    }
}
