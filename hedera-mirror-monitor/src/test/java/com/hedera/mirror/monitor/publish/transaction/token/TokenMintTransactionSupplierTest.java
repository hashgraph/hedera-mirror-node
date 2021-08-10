package com.hedera.mirror.monitor.publish.transaction.token;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.TokenMintTransaction;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenMintTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();

        TokenMintTransaction expected = new TokenMintTransaction()
                .setAmount(1)
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node minted test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomFungibleData() {
        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setAmount(2);
        tokenMintTransactionSupplier.setMaxTransactionFee(1);
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenMintTransactionSupplier.setType(TokenType.FUNGIBLE_COMMON);
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();

        TokenMintTransaction expected = new TokenMintTransaction()
                .setAmount(2)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node minted test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomNonFungibleeDataMessageSize() {
        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setAmount(2);
        tokenMintTransactionSupplier.setMaxTransactionFee(1);
        tokenMintTransactionSupplier.setMetadataSize(14);
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenMintTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();

        TokenMintTransaction expected = new TokenMintTransaction()
                .setMaxTransactionFee(ONE_TINYBAR)
                .setMetadata(actual.getMetadata())
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node minted test token"),
                () -> assertThat(actual.getMetadata().size()).isEqualTo(2),
                () -> assertThat(actual.getMetadata().get(0).length).isEqualTo(14),
                () -> assertThat(actual.getMetadata().get(1).length).isEqualTo(14),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomNonFungibleeDataMessage() {
        String metadata = "TokenMintTransactionSupplierTest";

        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setAmount(2);
        tokenMintTransactionSupplier.setMaxTransactionFee(1);
        tokenMintTransactionSupplier.setMetadata(metadata);
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenMintTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();

        TokenMintTransaction expected = new TokenMintTransaction()
                .setMaxTransactionFee(ONE_TINYBAR)
                .setMetadata(actual.getMetadata())
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node minted test token"),
                () -> assertThat(actual.getMetadata().size()).isEqualTo(2),
                () -> assertThat(actual.getMetadata().get(0)).isEqualTo(metadata.getBytes(StandardCharsets.UTF_8)),
                () -> assertThat(actual.getMetadata().get(1)).isEqualTo(metadata.getBytes(StandardCharsets.UTF_8)),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
