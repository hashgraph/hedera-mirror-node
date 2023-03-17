package com.hedera.mirror.monitor.publish.transaction;

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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.monitor.publish.transaction.account.AccountCreateTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.account.AccountDeleteTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.account.AccountUpdateTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.account.CryptoTransferTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.consensus.ConsensusCreateTopicTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.consensus.ConsensusDeleteTopicTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.consensus.ConsensusSubmitMessageTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.consensus.ConsensusUpdateTopicTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.schedule.ScheduleCreateTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.schedule.ScheduleDeleteTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.schedule.ScheduleSignTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenAssociateTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenBurnTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenCreateTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenDeleteTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenDissociateTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenFreezeTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenGrantKycTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenMintTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenPauseTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenRevokeKycTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenUnfreezeTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenUnpauseTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenUpdateTransactionSupplier;
import com.hedera.mirror.monitor.publish.transaction.token.TokenWipeTransactionSupplier;

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
    SCHEDULE_CREATE(ScheduleCreateTransactionSupplier.class),
    SCHEDULE_DELETE(ScheduleDeleteTransactionSupplier.class),
    SCHEDULE_SIGN(ScheduleSignTransactionSupplier.class),
    TOKEN_ASSOCIATE(TokenAssociateTransactionSupplier.class),
    TOKEN_BURN(TokenBurnTransactionSupplier.class),
    TOKEN_CREATE(TokenCreateTransactionSupplier.class),
    TOKEN_DELETE(TokenDeleteTransactionSupplier.class),
    TOKEN_DISSOCIATE(TokenDissociateTransactionSupplier.class),
    TOKEN_FREEZE(TokenFreezeTransactionSupplier.class),
    TOKEN_GRANT_KYC(TokenGrantKycTransactionSupplier.class),
    TOKEN_MINT(TokenMintTransactionSupplier.class),
    TOKEN_PAUSE(TokenPauseTransactionSupplier.class),
    TOKEN_REVOKE_KYC(TokenRevokeKycTransactionSupplier.class),
    TOKEN_UNFREEZE(TokenUnfreezeTransactionSupplier.class),
    TOKEN_UNPAUSE(TokenUnpauseTransactionSupplier.class),
    TOKEN_UPDATE(TokenUpdateTransactionSupplier.class),
    TOKEN_WIPE(TokenWipeTransactionSupplier.class);

    private final Class<? extends TransactionSupplier<?>> supplier;
}
