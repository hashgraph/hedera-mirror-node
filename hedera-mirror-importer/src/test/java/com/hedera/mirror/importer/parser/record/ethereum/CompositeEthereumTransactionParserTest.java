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

import static com.hedera.mirror.importer.parser.domain.RecordItemBuilder.LONDON_RAW_TX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CompositeEthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {

    public static final String BERLIN_RAW_TX_1 =
            "01f87182012a8085a54f4c3c00832dc6c094000000000000000000000000000000000000052d8502540be40083123456c001a04d83230d6c19076fa42ef92f88d2cb0ae917b58640cc86f221a2e15b1736714fa03a4643759236b06b6abb31713ad694ab3b7ac5760f183c46f448260b08252b58";
    public static final String LONDON_RAW_TX_2 =
            "02f902e082012a80a00000000000000000000000000000000000000000000000000000000000004e20a0000000000000000000000000000000000000000000000000000000746a528800830f42408080b9024d608060405261023a806100136000396000f3fe60806040526004361061003f5760003560e01c806312065fe01461008f5780633ccfd60b146100ba5780636f64234e146100d1578063b6b55f251461012c575b3373ffffffffffffffffffffffffffffffffffffffff167ff1b03f708b9c39f453fe3f0cef84164c7d6f7df836df0796e1e9c2bce6ee397e346040518082815260200191505060405180910390a2005b34801561009b57600080fd5b506100a461015a565b6040518082815260200191505060405180910390f35b3480156100c657600080fd5b506100cf610162565b005b3480156100dd57600080fd5b5061012a600480360360408110156100f457600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff169060200190929190803590602001909291905050506101ab565b005b6101586004803603602081101561014257600080fd5b81019080803590602001909291905050506101f6565b005b600047905090565b3373ffffffffffffffffffffffffffffffffffffffff166108fc479081150290604051600060405180830381858888f193505050501580156101a8573d6000803e3d6000fd5b50565b8173ffffffffffffffffffffffffffffffffffffffff166108fc829081150290604051600060405180830381858888f193505050501580156101f1573d6000803e3d6000fd5b505050565b80341461020257600080fd5b5056fea265627a7a72315820f8f84fc31a845064b5781e908316f3c591157962deabb0fd424ed54f256400f964736f6c63430005110032c001a01f7e8e436e6035ef7e5cd1387e2ad679e74d6a78a2736efe3dee72e531e28505a042b40a9cf56aad4530a5beaa8623f1ac3554d59ac1e927c672287eb45bfe7b8d";

    static final String LESS_THAN_ERROR_MESSAGE = "Ethereum transaction bytes length is less than 2 bytes in length";

    @BeforeAll
    static void beforeAll() {
        ethereumTransactionParser = new CompositeEthereumTransactionParser(
                new LegacyEthereumTransactionParser(),
                new Eip2930EthereumTransactionParser(),
                new Eip1559EthereumTransactionParser());
    }

    @SneakyThrows
    @Override
    public byte[] getTransactionBytes() {
        return Hex.decodeHex(LONDON_RAW_TX);
    }

    @Test
    void decodeNullBytes() {
        assertThatThrownBy(() -> ethereumTransactionParser.decode(null))
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessage(LESS_THAN_ERROR_MESSAGE);
    }

    @Test
    void decodeEmptyBytes() {
        assertThatThrownBy(() -> ethereumTransactionParser.decode(new byte[0]))
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessage(LESS_THAN_ERROR_MESSAGE);
    }

    @Test
    void decodeLessThanMinByteSize() {
        assertThatThrownBy(() -> ethereumTransactionParser.decode(new byte[] {1}))
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessage(LESS_THAN_ERROR_MESSAGE);
    }

    @SneakyThrows
    @Test
    void decodeEip1559WithAlternate2ndByte() {
        var ethereumTransaction = ethereumTransactionParser.decode(Hex.decodeHex(LONDON_RAW_TX_2));
        validateEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction.getType()).isEqualTo(Eip1559EthereumTransactionParser.EIP1559_TYPE_BYTE);
    }

    @SneakyThrows
    @Test
    void decodeEip2930() {
        var ethereumTransaction = ethereumTransactionParser.decode(Hex.decodeHex(BERLIN_RAW_TX_1));
        validateEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction.getType()).isEqualTo(Eip2930EthereumTransactionParser.EIP2930_TYPE_BYTE);
    }

    @SneakyThrows
    @Test
    void unsupportedEthereumTransaction() {
        byte[] unsupportedTx = Hex.decodeHex("33" + BERLIN_RAW_TX_1.substring(2));
        assertThrows(InvalidDatasetException.class, () -> ethereumTransactionParser.decode(unsupportedTx));
    }

    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction).isNotNull();
    }
}
