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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.exception.AliasNotFoundException;
import com.hedera.mirror.importer.parser.PartialDataAction;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

class CryptoApproveAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private long consensusTimestamp;

    private CryptoAllowance expectedCryptoAllowance;

    private Nft expectedNft;

    private NftAllowance expectedNftAllowance;

    private TokenAllowance expectedTokenAllowance;

    private RecordParserProperties recordParserProperties;

    private EntityId payerAccountId;

    @BeforeEach
    void beforeEach() {
        consensusTimestamp = DomainUtils.timestampInNanosMax(recordItemBuilder.timestamp());
        payerAccountId = EntityId.of(recordItemBuilder.accountId());
        var cryptoOwner = recordItemBuilder.accountId();
        expectedCryptoAllowance = CryptoAllowance.builder()
                .amount(100L)
                .owner(cryptoOwner.getAccountNum())
                .payerAccountId(payerAccountId)
                .spender(recordItemBuilder.accountId().getAccountNum())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        when(entityIdService.lookup(cryptoOwner)).thenReturn(EntityId.of(cryptoOwner));
        var nftOwner = recordItemBuilder.accountId();
        var nftTokenId = recordItemBuilder.tokenId().getTokenNum();
        expectedNft = Nft.builder()
                .id(new NftId(1L, EntityId.of(nftTokenId, EntityType.TOKEN)))
                .accountId(EntityId.of(nftOwner))
                .delegatingSpender(EntityId.EMPTY)
                .modifiedTimestamp(consensusTimestamp)
                .spender(EntityId.of(recordItemBuilder.accountId()))
                .build();
        when(entityIdService.lookup(nftOwner)).thenReturn(expectedNft.getAccountId());
        expectedNftAllowance = NftAllowance.builder()
                .approvedForAll(true)
                .owner(expectedNft.getAccountId().getId())
                .payerAccountId(payerAccountId)
                .spender(expectedNft.getSpender().getId())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .tokenId(nftTokenId)
                .build();
        var tokenOwner = recordItemBuilder.accountId();
        expectedTokenAllowance = TokenAllowance.builder()
                .amount(200L)
                .owner(tokenOwner.getAccountNum())
                .payerAccountId(payerAccountId)
                .spender(recordItemBuilder.accountId().getAccountNum())
                .timestampRange(Range.atLeast(consensusTimestamp))
                .tokenId(recordItemBuilder.tokenId().getTokenNum())
                .build();
        when(entityIdService.lookup(tokenOwner)).thenReturn(EntityId.of(tokenOwner));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        recordParserProperties = new RecordParserProperties();
        return new CryptoApproveAllowanceTransactionHandler(entityIdService, entityListener, recordParserProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionUnsuccessful() {
        var transaction = new Transaction();
        RecordItem recordItem = recordItemBuilder.cryptoApproveAllowance()
                .receipt(r -> r.setStatus(ResponseCodeEnum.ACCOUNT_DELETED))
                .build();
        transactionHandler.updateTransaction(transaction, recordItem);
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder.cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)).get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(null);
    }

    @Test
    void updateTransactionSuccessfulWithImplicitOwner() {
        var recordItem = recordItemBuilder.cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBody(b -> {
                    b.getCryptoAllowancesBuilderList().forEach(builder -> builder.clearOwner());
                    b.getNftAllowancesBuilderList().forEach(builder -> builder.clearOwner());
                    b.getTokenAllowancesBuilderList().forEach(builder -> builder.clearOwner());
                })
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var effectiveOwner = recordItem.getPayerAccountId().getId();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp)).get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(effectiveOwner);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartialDataAction.class, names = {"DEFAULT", "ERROR"})
    void updateTransactionThrowsWithAliasNotFound(PartialDataAction partialDataAction) {
        // given
        recordParserProperties.setPartialDataAction(partialDataAction);
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.cryptoApproveAllowance().transactionBody(b -> {
            b.getCryptoAllowancesBuilderList().forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
            b.getNftAllowancesBuilderList().forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
            b.getTokenAllowancesBuilderList().forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
        }).build();
        var transaction = domainBuilder.transaction().get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenThrow(new AliasNotFoundException("alias", EntityType.ACCOUNT));

        // when, then
        assertThrows(AliasNotFoundException.class, () -> transactionHandler.updateTransaction(transaction, recordItem));
    }

    @Test
    void updateTransactionWithAliasNotFoundAndPartialDataActionSkip() {
        recordParserProperties.setPartialDataAction(PartialDataAction.SKIP);
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.cryptoApproveAllowance().transactionBody(b -> {
            b.getCryptoAllowancesBuilderList().forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
            b.getNftAllowancesBuilderList().forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
            b.getTokenAllowancesBuilderList().forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
        }).build();
        var transaction = domainBuilder.transaction().get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenThrow(new AliasNotFoundException("alias", EntityType.ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionWithAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var ownerEntityId = EntityId.of(recordItemBuilder.accountId());
        var recordItem = recordItemBuilder.cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBody(b -> {
                    b.getCryptoAllowancesBuilderList().forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getNftAllowancesBuilderList().forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getTokenAllowancesBuilderList().forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                })
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build())).thenReturn(ownerEntityId);
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(ownerEntityId.getId());
    }

    private void assertAllowances(Long effectiveOwner) {
        if (effectiveOwner != null) {
            expectedCryptoAllowance.setOwner(effectiveOwner);
            expectedNft.setAccountId(EntityId.of(effectiveOwner, EntityType.ACCOUNT));
            expectedNftAllowance.setOwner(effectiveOwner);
            expectedTokenAllowance.setOwner(effectiveOwner);
        }

        verify(entityListener, times(1)).onCryptoAllowance(assertArg(t -> assertEquals(expectedCryptoAllowance, t)));
        verify(entityListener, times(1)).onNft(assertArg(t -> assertEquals(expectedNft, t)));
        verify(entityListener, times(1)).onNftAllowance(assertArg(t -> assertEquals(expectedNftAllowance, t)));
        verify(entityListener, times(1)).onTokenAllowance(assertArg(t -> assertEquals(expectedTokenAllowance, t)));
    }

    private void customizeTransactionBody(CryptoApproveAllowanceTransactionBody.Builder builder) {
        builder.clear();

        // duplicate with different amount
        builder.addCryptoAllowances(com.hederahashgraph.api.proto.java.CryptoAllowance.newBuilder()
                .setAmount(expectedCryptoAllowance.getAmount() - 10)
                .setOwner(AccountID.newBuilder().setAccountNum(expectedCryptoAllowance.getOwner()))
                .setSpender(AccountID.newBuilder().setAccountNum(expectedCryptoAllowance.getSpender()))
        );
        // the last one is honored
        builder.addCryptoAllowances(com.hederahashgraph.api.proto.java.CryptoAllowance.newBuilder()
                .setAmount(expectedCryptoAllowance.getAmount())
                .setOwner(AccountID.newBuilder().setAccountNum(expectedCryptoAllowance.getOwner()))
                .setSpender(AccountID.newBuilder().setAccountNum(expectedCryptoAllowance.getSpender()))
        );

        // duplicate nft allowance by serial
        builder.addNftAllowances(com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
                .setOwner(AccountID.newBuilder().setAccountNum(expectedNft.getAccountId().getEntityNum()))
                .addSerialNumbers(expectedNft.getId().getSerialNumber())
                .setSpender(AccountID.newBuilder().setAccountNum(expectedNft.getSpender().getEntityNum() + 1))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedNft.getId().getTokenId().getEntityNum()))
        );
        // duplicate nft approved for all allowance, approved for all flag is flipped from the last one
        builder.addNftAllowances(com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(!expectedNftAllowance.isApprovedForAll()))
                .setOwner(AccountID.newBuilder().setAccountNum(expectedNft.getAccountId().getEntityNum()))
                .addSerialNumbers(expectedNft.getId().getSerialNumber())
                .setSpender(AccountID.newBuilder().setAccountNum(expectedNft.getSpender().getEntityNum()))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedNft.getId().getTokenId().getEntityNum()))
        );
        // the last one is honored
        builder.addNftAllowances(com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(expectedNftAllowance.isApprovedForAll()))
                .setOwner(AccountID.newBuilder().setAccountNum(expectedNft.getAccountId().getEntityNum()))
                .addSerialNumbers(expectedNft.getId().getSerialNumber())
                .setSpender(AccountID.newBuilder().setAccountNum(expectedNft.getSpender().getEntityNum()))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedNft.getId().getTokenId().getEntityNum()))
        );

        // duplicate token allowance
        builder.addTokenAllowances(com.hederahashgraph.api.proto.java.TokenAllowance.newBuilder()
                .setAmount(expectedTokenAllowance.getAmount() - 10)
                .setOwner(AccountID.newBuilder().setAccountNum(expectedTokenAllowance.getOwner()))
                .setSpender(AccountID.newBuilder().setAccountNum(expectedTokenAllowance.getSpender()))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedTokenAllowance.getTokenId()))
        );
        // the last one is honored
        builder.addTokenAllowances(com.hederahashgraph.api.proto.java.TokenAllowance.newBuilder()
                .setAmount(expectedTokenAllowance.getAmount())
                .setOwner(AccountID.newBuilder().setAccountNum(expectedTokenAllowance.getOwner()))
                .setSpender(AccountID.newBuilder().setAccountNum(expectedTokenAllowance.getSpender()))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedTokenAllowance.getTokenId()))
        );
    }

    private void setTransactionPayer(TransactionBody.Builder builder) {
        builder.getTransactionIDBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(payerAccountId.getEntityNum()));
    }
}
