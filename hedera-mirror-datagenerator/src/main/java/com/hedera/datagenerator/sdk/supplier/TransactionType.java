package com.hedera.datagenerator.sdk.supplier;

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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.hedera.datagenerator.sdk.supplier.account.AccountCreateTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.account.AccountDeleteTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.account.AccountUpdateTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.account.CryptoTransferTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.consensus.ConsensusCreateTopicTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.consensus.ConsensusDeleteTopicTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.consensus.ConsensusSubmitMessageTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.consensus.ConsensusUpdateTopicTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenAssociateTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenBurnTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenCreateTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenDeleteTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenDissociateTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenFreezeTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenGrantKycTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenMintTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenRevokeKycTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenUnfreezeTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenUpdateTransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.token.TokenWipeTransactionSupplier;

@Getter
@RequiredArgsConstructor
public enum TransactionType {

    ACCOUNT_CREATE(AccountCreateTransactionSupplier.class),
    ACCOUNT_DELETE(AccountDeleteTransactionSupplier.class),
    ACCOUNT_UPDATE(AccountUpdateTransactionSupplier.class),
    CONSENSUS_CREATE_TOPIC(ConsensusCreateTopicTransactionSupplier.class),
    CONSENSUS_DELETE_TOPIC(ConsensusDeleteTopicTransactionSupplier.class),
    CONSENSUS_SUBMIT_MESSAGE(ConsensusSubmitMessageTransactionSupplier.class),
    CONSENSUS_UPDATE_TOPIC(ConsensusUpdateTopicTransactionSupplier.class),
    CRYPTO_TRANSFER(CryptoTransferTransactionSupplier.class),
    TOKEN_ASSOCIATE(TokenAssociateTransactionSupplier.class),
    TOKEN_BURN(TokenBurnTransactionSupplier.class),
    TOKEN_CREATE(TokenCreateTransactionSupplier.class),
    TOKEN_DELETE(TokenDeleteTransactionSupplier.class),
    TOKEN_DISSOCIATE(TokenDissociateTransactionSupplier.class),
    TOKEN_FREEZE(TokenFreezeTransactionSupplier.class),
    TOKEN_GRANT_KYC(TokenGrantKycTransactionSupplier.class),
    TOKEN_MINT(TokenMintTransactionSupplier.class),
    TOKEN_REVOKE_KYC(TokenRevokeKycTransactionSupplier.class),
    TOKEN_UNFREEZE(TokenUnfreezeTransactionSupplier.class),
    TOKEN_UPDATE(TokenUpdateTransactionSupplier.class),
    TOKEN_WIPE(TokenWipeTransactionSupplier.class);

    private final Class<? extends TransactionSupplier<?>> supplier;
}
