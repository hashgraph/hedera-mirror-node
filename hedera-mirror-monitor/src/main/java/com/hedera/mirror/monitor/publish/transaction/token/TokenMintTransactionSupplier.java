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

import java.nio.charset.StandardCharsets;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.monitor.Utility;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenMintTransaction;
import com.hedera.hashgraph.sdk.TokenType;

@Data
public class TokenMintTransactionSupplier implements TransactionSupplier<TokenMintTransaction> {

    @Min(1)
    private long amount = 1;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotNull
    private String metadata = StringUtils.EMPTY;

    @Min(1)
    private int metadataSize = 16;

    @NotBlank
    private String tokenId;

    @NotNull
    private TokenType type = TokenType.FUNGIBLE_COMMON;

    @Override
    public TokenMintTransaction get() {

        TokenMintTransaction transaction = new TokenMintTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTokenId(TokenId.fromString(tokenId))
                .setTransactionMemo(Utility.getMemo("Mirror node minted test token"));
        switch (type) {
            case FUNGIBLE_COMMON:
                transaction.setAmount(amount);
                break;
            case NON_FUNGIBLE_UNIQUE:
                for (int i = 0; i < amount; i++) {
                    transaction.addMetadata(!metadata.isEmpty() ? metadata.getBytes(StandardCharsets.UTF_8) : Utility
                            .generateMessage(metadataSize));
                }
        }
        return transaction;
    }
}
