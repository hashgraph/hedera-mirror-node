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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import java.math.BigInteger;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeAll;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;

public class LegacyEthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {
    static final String RAW_TX_TYPE_0 =
            "f864012f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc18180827653820277a0f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2fa00c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792";

    @BeforeAll
    static void beforeAll() {
        ethereumTransactionParser = new Eip1559EthereumTransactionParser();
    }

    @Override
    public EthereumTransactionBody getTransactionBody() {
        var body = EthereumTransactionBody.newBuilder()
                .setEthereumData(ByteString.copyFrom(Hex.decode(RAW_TX_TYPE_0)))
                .build();
        return body;
    }

    @Override
    public void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction)
                .isNotNull()
                .satisfies(t -> assertThat(t.getType()).isEqualTo(0))
                .satisfies(t -> assertThat(t.getChainId()).isEqualTo("012a"))
                .satisfies(t -> assertThat(t.getNonce()).isEqualTo(1))
                .satisfies(t -> assertThat(Hex.toHexString(t.getGasPrice())).isEqualTo("2f"))
                .satisfies(t -> assertThat(t.getMaxPriorityFeePerGas()).isNull())
                .satisfies(t -> assertThat(t.getGasPrice()).isNull())
                .satisfies(t -> assertThat(t.getGasLimit()).isEqualTo(98_304L))
                .satisfies(t -> assertThat(Hex.toHexString(t.getToAddress())).isEqualTo(
                        "7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181"))
                .satisfies(t -> assertThat(t.getValue()).isEqualTo(BigInteger.ZERO.toByteArray()));
    }
}
