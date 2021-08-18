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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
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

        assertThat(actual)
                .returns(1L, TokenMintTransaction::getAmount)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenMintTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenMintTransaction::getMetadata)
                .returns(TOKEN_ID, TokenMintTransaction::getTokenId)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node minted test token"));
    }

    @Test
    void createWithCustomFungibleData() {
        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setAmount(2);
        tokenMintTransactionSupplier.setMaxTransactionFee(1);
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenMintTransactionSupplier.setType(TokenType.FUNGIBLE_COMMON);
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();

        assertThat(actual)
                .returns(2L, TokenMintTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenMintTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenMintTransaction::getMetadata)
                .returns(TOKEN_ID, TokenMintTransaction::getTokenId)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node minted test token"));
    }

    @Test
    void createWithCustomNonFungibleDataMessageSize() {
        TokenMintTransactionSupplier tokenMintTransactionSupplier = new TokenMintTransactionSupplier();
        tokenMintTransactionSupplier.setAmount(2);
        tokenMintTransactionSupplier.setMaxTransactionFee(1);
        tokenMintTransactionSupplier.setMetadataSize(14);
        tokenMintTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenMintTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenMintTransaction actual = tokenMintTransactionSupplier.get();

        assertThat(actual)
                .returns(0L, TokenMintTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenMintTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenMintTransaction::getTokenId)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node minted test token"))
                .extracting(TokenMintTransaction::getMetadata)
                .returns(2, List::size)
                .returns(14, metadataList -> metadataList.get(0).length)
                .returns(14, metadataList -> metadataList.get(1).length);
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

        assertThat(actual)
                .returns(0L, TokenMintTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenMintTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenMintTransaction::getTokenId)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node minted test token"))
                .extracting(TokenMintTransaction::getMetadata)
                .returns(2, List::size)
                .returns(metadata.getBytes(StandardCharsets.UTF_8), metadataList -> metadataList.get(0))
                .returns(metadata.getBytes(StandardCharsets.UTF_8), metadataList -> metadataList.get(1));
    }
}
