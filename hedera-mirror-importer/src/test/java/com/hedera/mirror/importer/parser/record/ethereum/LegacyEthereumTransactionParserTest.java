package com.hedera.mirror.importer.parser.record.ethereum;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

@SuppressWarnings("java:S5786")
public class LegacyEthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {
    public static final String LEGACY_RAW_TX =
            "f864012f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc18180827653820277a0f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2fa00c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792";
    public static final String EIP155_RAW_TX =
            "f86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83";

    @BeforeAll
    static void beforeAll() {
        ethereumTransactionParser = new LegacyEthereumTransactionParser();
    }

    @SneakyThrows
    @Test
    void eip155Decode() {
        var ethereumTransaction = ethereumTransactionParser.decode(Hex.decodeHex(EIP155_RAW_TX));

        assertThat(ethereumTransaction)
                .isNotNull()
                .satisfies(t -> assertThat(t.getType()).isZero())
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getChainId())).isEqualTo("01"))
                .satisfies(t -> assertThat(t.getNonce()).isEqualTo(9))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getGasPrice())).isEqualTo("04a817c800"))
                .satisfies(t -> assertThat(t.getMaxPriorityFeePerGas()).isNull())
                .satisfies(t -> assertThat(t.getMaxFeePerGas()).isNull())
                .satisfies(t -> assertThat(t.getGasLimit()).isEqualTo(21_000L))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getToAddress())).isEqualTo(
                        "3535353535353535353535353535353535353535"))
                .satisfies(t -> assertThat(t.getValue()).isEqualTo(new BigInteger("0de0b6b3a7640000", 16).toByteArray()))
                .satisfies(t -> assertThat(t.getCallData()).isEmpty())
                .satisfies(t -> assertThat(t.getAccessList()).isNull())
                .satisfies(t -> assertThat(t.getRecoveryId()).isZero())
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureV())).isEqualTo("25"))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureR())).isEqualTo(
                        "28ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276"))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureS())).isEqualTo(
                        "67cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83"));
    }

    @Test
    void decodeIncorrectRlpItemListSize() {
        var ethereumTransactionBytes = RLPEncoder.list(Integers.toBytes(1));

        assertThatThrownBy(() -> ethereumTransactionParser.decode(ethereumTransactionBytes))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage("Unable to decode Legacy ethereum transaction bytes, RLPItem list size was 1 but should " +
                        "be 9");
    }

    @SneakyThrows
    @Override
    public byte[] getTransactionBytes() {
        return Hex.decodeHex(LEGACY_RAW_TX);
    }

    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull()
                .satisfies(t -> assertThat(t.getType()).isZero())
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getChainId())).isEqualTo("012a"))
                .satisfies(t -> assertThat(t.getNonce()).isEqualTo(1))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getGasPrice())).isEqualTo("2f"))
                .satisfies(t -> assertThat(t.getMaxPriorityFeePerGas()).isNull())
                .satisfies(t -> assertThat(t.getMaxFeePerGas()).isNull())
                .satisfies(t -> assertThat(t.getGasLimit()).isEqualTo(98_304L))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getToAddress())).isEqualTo(
                        "7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181"))
                .satisfies(t -> assertThat(t.getValue()).isEqualTo(BigInteger.ZERO.toByteArray()))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getCallData())).isEqualTo("7653"))
                .satisfies(t -> assertThat(t.getAccessList()).isNull())
                .satisfies(t -> assertThat(t.getRecoveryId()).isZero())
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureV())).isEqualTo("0277"))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureR())).isEqualTo(
                        "f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2f"))
                .satisfies(t -> assertThat(Hex.encodeHexString(t.getSignatureS())).isEqualTo(
                        "0c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792"));
    }
}
