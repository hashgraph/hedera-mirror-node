/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.LONDON_RAW_TX;
import static com.hedera.mirror.importer.parser.record.ethereum.Eip2930EthereumTransactionParserTest.EIP_2930_RAW_TX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.exception.InvalidEthereumBytesException;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class Eip1559EthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {

    public Eip1559EthereumTransactionParserTest(Eip1559EthereumTransactionParser ethereumTransactionParser) {
        super(ethereumTransactionParser);
    }

    @Override
    public byte[] getTransactionBytes() {
        return LONDON_RAW_TX;
    }

    @Test
    void decodeLegacyType() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(1), new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP1559 ethereum transaction bytes, First byte was 1 but should be 2");
    }

    @Test
    void decodeNonListRlpItem() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(2), Integers.toBytes(1));

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP1559 ethereum transaction bytes, Second RLPItem was not a list");
    }

    @Test
    void decodeIncorrectRlpItemListSize() {
        var ethereumTransactionBytes = RLPEncoder.sequence(Integers.toBytes(2), new Object[] {});

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode EIP1559 ethereum transaction bytes, RLP list size was 0 but expected 12");
    }

    @Test
    void getHashIncorrectTransactionType(CapturedOutput capturedOutput) {
        // given, when
        var actual = ethereumTransactionParser.getHash(
                EMPTY_BYTE_ARRAY, domainBuilder.entityId(), domainBuilder.timestamp(), EIP_2930_RAW_TX);

        // then
        softly.assertThat(actual).isEmpty();
        softly.assertThat(capturedOutput).contains("Unable to decode EIP1559 ethereum transaction bytes");
    }

    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull()
                .returns(Eip1559EthereumTransactionParser.EIP1559_TYPE_BYTE, EthereumTransaction::getType)
                .returns(Hex.decode("012a"), EthereumTransaction::getChainId)
                .returns(2L, EthereumTransaction::getNonce)
                .returns(null, EthereumTransaction::getGasPrice)
                .returns(Hex.decode("2f"), EthereumTransaction::getMaxPriorityFeePerGas)
                .returns(Hex.decode("2f"), EthereumTransaction::getMaxFeePerGas)
                .returns(98_304L, EthereumTransaction::getGasLimit)
                .returns(Hex.decode("7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181"), EthereumTransaction::getToAddress)
                .returns(Hex.decode("0de0b6b3a7640000"), EthereumTransaction::getValue)
                .returns(Hex.decode("123456"), EthereumTransaction::getCallData)
                .returns(Hex.decode(""), EthereumTransaction::getAccessList)
                .returns(1, EthereumTransaction::getRecoveryId)
                .returns(null, EthereumTransaction::getSignatureV)
                .returns(
                        Hex.decode("df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479"),
                        EthereumTransaction::getSignatureR)
                .returns(
                        Hex.decode("1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66"),
                        EthereumTransaction::getSignatureS);
    }
}
