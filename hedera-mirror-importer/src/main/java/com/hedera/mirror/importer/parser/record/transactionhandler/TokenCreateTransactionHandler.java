package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import static com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum.NOT_APPLICABLE;
import static com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum.UNFROZEN;
import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.hederahashgraph.api.proto.java.TokenAssociation;
import javax.inject.Named;
import lombok.CustomLog;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@CustomLog
@Named
class TokenCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final EntityProperties entityProperties;
    private final TokenFeeScheduleUpdateTransactionHandler tokenFeeScheduleUpdateTransactionHandler;

    TokenCreateTransactionHandler(EntityIdService entityIdService, EntityListener entityListener,
                                  EntityProperties entityProperties,
                                  TokenFeeScheduleUpdateTransactionHandler tokenFeeScheduleUpdateTransactionHandler) {
        super(entityIdService, entityListener, TransactionType.TOKENCREATION);
        this.entityProperties = entityProperties;
        this.tokenFeeScheduleUpdateTransactionHandler = tokenFeeScheduleUpdateTransactionHandler;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionRecord().getReceipt().getTokenID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getTokenCreation();

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasAutoRenewAccount()) {
            var autoRenewAccountId = entityIdService.lookup(transactionBody.getAutoRenewAccount())
                    .orElse(EntityId.EMPTY);
            if (EntityId.isEmpty(autoRenewAccountId)) {
                log.error(RECOVERABLE_ERROR + "Invalid autoRenewAccountId at {}",
                        recordItem.getConsensusTimestamp());
            } else {
                entity.setAutoRenewAccountId(autoRenewAccountId.getId());
            }
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpiry()) {
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(transactionBody.getExpiry()));
        }

        entity.setMemo(transactionBody.getMemo());
        entityListener.onEntity(entity);
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var tokenCreateTransactionBody = recordItem.getTransactionBody().getTokenCreation();
        long consensusTimestamp = transaction.getConsensusTimestamp();
        var tokenId = transaction.getEntityId();
        var treasury = EntityId.of(tokenCreateTransactionBody.getTreasury());

        var token = new Token();
        token.setCreatedTimestamp(consensusTimestamp);
        token.setDecimals(tokenCreateTransactionBody.getDecimals());
        token.setFreezeDefault(tokenCreateTransactionBody.getFreezeDefault());
        token.setInitialSupply(tokenCreateTransactionBody.getInitialSupply());
        token.setMaxSupply(tokenCreateTransactionBody.getMaxSupply());
        token.setModifiedTimestamp(consensusTimestamp);
        token.setName(tokenCreateTransactionBody.getName());
        token.setSupplyType(TokenSupplyTypeEnum.fromId(tokenCreateTransactionBody.getSupplyTypeValue()));
        token.setSymbol(tokenCreateTransactionBody.getSymbol());
        token.setTokenId(new TokenId(tokenId));
        token.setTotalSupply(tokenCreateTransactionBody.getInitialSupply());
        token.setTreasuryAccountId(treasury);
        token.setType(TokenTypeEnum.fromId(tokenCreateTransactionBody.getTokenTypeValue()));

        if (tokenCreateTransactionBody.hasFeeScheduleKey()) {
            token.setFeeScheduleKey(tokenCreateTransactionBody.getFeeScheduleKey().toByteArray());
        }

        if (tokenCreateTransactionBody.hasFreezeKey()) {
            token.setFreezeKey(tokenCreateTransactionBody.getFreezeKey().toByteArray());
        }

        if (tokenCreateTransactionBody.hasKycKey()) {
            token.setKycKey(tokenCreateTransactionBody.getKycKey().toByteArray());
        }

        if (tokenCreateTransactionBody.hasPauseKey()) {
            token.setPauseKey(tokenCreateTransactionBody.getPauseKey().toByteArray());
            token.setPauseStatus(TokenPauseStatusEnum.UNPAUSED);
        } else {
            token.setPauseStatus(TokenPauseStatusEnum.NOT_APPLICABLE);
        }

        if (tokenCreateTransactionBody.hasSupplyKey()) {
            token.setSupplyKey(tokenCreateTransactionBody.getSupplyKey().toByteArray());
        }

        if (tokenCreateTransactionBody.hasWipeKey()) {
            token.setWipeKey(tokenCreateTransactionBody.getWipeKey().toByteArray());
        }

        var customFees = tokenCreateTransactionBody.getCustomFeesList();
        var autoAssociatedAccounts = tokenFeeScheduleUpdateTransactionHandler.updateCustomFees(transaction, customFees);
        autoAssociatedAccounts.add(treasury);

        // automatic_token_associations does not exist prior to services 0.18.0
        if (recordItem.getTransactionRecord().getAutomaticTokenAssociationsCount() > 0) {
            autoAssociatedAccounts.clear();
            recordItem.getTransactionRecord().getAutomaticTokenAssociationsList().stream()
                    .map(TokenAssociation::getAccountId)
                    .map(EntityId::of)
                    .forEach(autoAssociatedAccounts::add);
        }

        var freezeStatus = token.getFreezeKey() != null ? UNFROZEN : NOT_APPLICABLE;
        var kycStatus = token.getKycKey() != null ? TokenKycStatusEnum.GRANTED : TokenKycStatusEnum.NOT_APPLICABLE;

        autoAssociatedAccounts.forEach(account -> {
            var tokenAccount = new TokenAccount();
            tokenAccount.setAccountId(account.getId());
            tokenAccount.setAssociated(true);
            tokenAccount.setAutomaticAssociation(false);
            tokenAccount.setCreatedTimestamp(consensusTimestamp);
            tokenAccount.setFreezeStatus(freezeStatus);
            tokenAccount.setKycStatus(kycStatus);
            tokenAccount.setTimestampLower(consensusTimestamp);
            tokenAccount.setTokenId(tokenId.getId());
            entityListener.onTokenAccount(tokenAccount);
        });

        entityListener.onToken(token);
    }
}
