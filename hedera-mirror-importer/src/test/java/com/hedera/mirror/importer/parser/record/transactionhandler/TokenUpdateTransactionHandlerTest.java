package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.NftTransferId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.AliasNotFoundException;
import com.hedera.mirror.importer.parser.PartialDataAction;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.repository.NftRepository;

@ExtendWith(MockitoExtension.class)
class TokenUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private NftRepository nftRepository;

    private RecordParserProperties recordParserProperties;

    @Override
    protected TransactionHandler getTransactionHandler() {
        recordParserProperties = new RecordParserProperties();
        return new TokenUpdateTransactionHandler(entityIdService, entityListener, nftRepository,
                recordParserProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
                        .setToken(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM))
                        .setAdminKey(DEFAULT_KEY)
                        .setExpiry(Timestamp.newBuilder().setSeconds(360))
                        .setKycKey(DEFAULT_KEY)
                        .setFreezeKey(DEFAULT_KEY)
                        .setSymbol("SYMBOL")
                        .setTreasury(AccountID.newBuilder().setAccountNum(1))
                        .setAutoRenewAccount(AccountID.newBuilder().setAccountNum(2))
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(100))
                        .setName("token_name")
                        .setWipeKey(DEFAULT_KEY));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @Test
    void updateTreasury() {
        AbstractEntity entity = getExpectedUpdatedEntity();
        AccountID previousAccountId = AccountID.newBuilder().setAccountNum(1L).build();
        AccountID newAccountId = AccountID.newBuilder().setAccountNum(2L).build();
        TokenID tokenID = TokenID.newBuilder().setTokenNum(3L).build();
        long consensusTimestamp = DomainUtils.timestampInNanosMax(MODIFIED_TIMESTAMP);
        TokenTransferList tokenTransferList = TokenTransferList.newBuilder()
                .setToken(tokenID)
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(newAccountId)
                        .setSenderAccountID(previousAccountId)
                        .setSerialNumber(NftTransferId.WILDCARD_SERIAL_NUMBER)
                        .build())
                .build();
        TransactionRecord record = getDefaultTransactionRecord().addTokenTransferLists(tokenTransferList).build();
        RecordItem recordItem = getRecordItem(getDefaultTransactionBody().build(), record);

        Transaction transaction = new Transaction();
        transaction.setEntityId(entity.toEntityId());
        transactionHandler.updateTransaction(transaction, recordItem);

        TransactionBody body = recordItem.getTransactionBody();
        var payerAccount = EntityId.of(body.getTransactionID().getAccountID()).toEntity().getId();
        verify(nftRepository).updateTreasury(tokenID.getTokenNum(), previousAccountId.getAccountNum(),
                newAccountId.getAccountNum(), consensusTimestamp, payerAccount, false);
    }

    @Test
    void noTreasuryUpdate() {
        AbstractEntity entity = getExpectedUpdatedEntity();
        TokenTransferList tokenTransferList = TokenTransferList.newBuilder()
                .setToken(TokenID.newBuilder().setTokenNum(3L).build())
                .addNftTransfers(NftTransfer.newBuilder()
                        .setReceiverAccountID(AccountID.newBuilder().setAccountNum(2L).build())
                        .setSenderAccountID(AccountID.newBuilder().setAccountNum(1L).build())
                        .setSerialNumber(1L) // Not wildcard
                        .build())
                .build();
        TransactionRecord record = getDefaultTransactionRecord().addTokenTransferLists(tokenTransferList).build();
        RecordItem recordItem = getRecordItem(getDefaultTransactionBody().build(), record);

        Transaction transaction = new Transaction();
        transaction.setEntityId(entity.toEntityId());
        transactionHandler.updateTransaction(transaction, recordItem);

        Mockito.verifyNoInteractions(nftRepository);
    }

    @Test
    void updateTransactionUnsuccessful() {
        RecordItem recordItem = recordItemBuilder.tokenUpdate()
                .receipt(r -> r.setStatus(ResponseCodeEnum.ACCOUNT_DELETED))
                .build();
        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionSuccessful() {
        RecordItem recordItem = recordItemBuilder.tokenUpdate().build();
        var tokenId = EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId)).get();
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(EntityIdEndec.decode(10, EntityType.ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertTokenUpdate(timestamp, tokenId, id -> assertEquals(10L, id));
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.tokenUpdate()
                .transactionBody(b -> b.getAutoRenewAccountBuilder().setAlias(alias))
                .build();
        var tokenId = EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(EntityIdEndec.decode(10L, ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertTokenUpdate(timestamp, tokenId, id -> assertEquals(10L, id));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartialDataAction.class, names = {"DEFAULT", "ERROR"})
    void updateTransactionThrowsWithAliasNotFound(PartialDataAction partialDataAction) {
        // given
        recordParserProperties.setPartialDataAction(partialDataAction);
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.tokenUpdate()
                .transactionBody(b -> b.getAutoRenewAccountBuilder().setAlias(alias))
                .build();
        var tokenId = EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenThrow(new AliasNotFoundException("alias", ACCOUNT));

        // when, then
        assertThrows(AliasNotFoundException.class, () -> transactionHandler.updateTransaction(transaction, recordItem));
    }

    @Test
    void updateTransactionWithAliasNotFoundAndPartialDataActionSkip() {
        recordParserProperties.setPartialDataAction(PartialDataAction.SKIP);
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.tokenUpdate()
                .transactionBody(b -> b.getAutoRenewAccountBuilder().setAlias(alias))
                .build();
        var tokenId = EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenThrow(new AliasNotFoundException("alias", ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertTokenUpdate(timestamp, tokenId, Assertions::assertNull);
    }

    void assertTokenUpdate(long timestamp, EntityId tokenId, Consumer<Long> assertAutoRenewAccountId) {
        verify(entityListener).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(e -> assertAutoRenewAccountId.accept(e.getAutoRenewAccountId()))
                .satisfies(e -> assertThat(e.getAutoRenewPeriod()).isPositive())
                .returns(null, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getExpirationTimestamp()).isPositive())
                .returns(tokenId.getId(), Entity::getId)
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(null, Entity::getMaxAutomaticTokenAssociations)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .returns(tokenId.getEntityNum(), Entity::getNum)
                .returns(null, Entity::getProxyAccountId)
                .satisfies(e -> assertThat(e.getPublicKey()).isNotEmpty())
                .returns(tokenId.getRealmNum(), Entity::getRealm)
                .returns(tokenId.getShardNum(), Entity::getShard)
                .returns(EntityType.TOKEN, Entity::getType)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)));
    }
}
