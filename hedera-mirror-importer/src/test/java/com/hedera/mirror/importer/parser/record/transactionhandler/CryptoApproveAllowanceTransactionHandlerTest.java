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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CryptoApproveAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private long consensusTimestamp;

    private CryptoAllowance expectedCryptoAllowance;

    private Nft expectedNft;

    private NftAllowance expectedNftAllowance;

    private TokenAllowance expectedTokenAllowance;

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
        when(entityIdService.lookup(cryptoOwner)).thenReturn(Optional.of(EntityId.of(cryptoOwner)));
        var nftOwner = recordItemBuilder.accountId();
        var nftTokenId = recordItemBuilder.tokenId().getTokenNum();
        expectedNft = Nft.builder()
                .accountId(EntityId.of(nftOwner))
                .delegatingSpender(EntityId.EMPTY)
                .serialNumber(1)
                .spender(EntityId.of(recordItemBuilder.accountId()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .tokenId(nftTokenId)
                .build();
        when(entityIdService.lookup(nftOwner)).thenReturn(Optional.of(expectedNft.getAccountId()));
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
        when(entityIdService.lookup(tokenOwner)).thenReturn(Optional.of(EntityId.of(tokenOwner)));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoApproveAllowanceTransactionHandler(
                entityIdService, entityListener, syntheticContractLogService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoApproveAllowance(
                        CryptoApproveAllowanceTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(null);
    }

    @Test
    void updateTransactionSuccessfulWithImplicitOwner() {
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
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
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(effectiveOwner);
    }

    @Test
    void updateTransactionWithEmptyEntityId() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBody(b -> {
                    b.getCryptoAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getNftAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getTokenAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                })
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var transaction = domainBuilder.transaction().get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(Optional.of(EntityId.EMPTY));
        transactionHandler.updateTransaction(transaction, recordItem);

        // The implicit entity id is used
        var effectiveOwner = recordItem.getPayerAccountId().getId();
        assertAllowances(effectiveOwner);
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void updateTransactionWithEmptyOwner(EntityId entityId) {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(b -> {
                    b.getCryptoAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().clear());
                    b.getNftAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getTokenAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                })
                .transactionBodyWrapper(w -> w.getTransactionIDBuilder()
                        .setAccountID(AccountID.newBuilder().setAccountNum(0)))
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var transaction = domainBuilder.transaction().get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(Optional.ofNullable(entityId));
        transactionHandler.updateTransaction(transaction, recordItem);

        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionWithAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var ownerEntityId = EntityId.of(recordItemBuilder.accountId());
        var recordItem = recordItemBuilder
                .cryptoApproveAllowance()
                .transactionBody(this::customizeTransactionBody)
                .transactionBody(b -> {
                    b.getCryptoAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getNftAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                    b.getTokenAllowancesBuilderList()
                            .forEach(builder -> builder.getOwnerBuilder().setAlias(alias));
                })
                .transactionBodyWrapper(this::setTransactionPayer)
                .record(r -> r.setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp)))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(Optional.of(ownerEntityId));
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
                .setSpender(AccountID.newBuilder().setAccountNum(expectedCryptoAllowance.getSpender())));
        // the last one is honored
        builder.addCryptoAllowances(com.hederahashgraph.api.proto.java.CryptoAllowance.newBuilder()
                .setAmount(expectedCryptoAllowance.getAmount())
                .setOwner(AccountID.newBuilder().setAccountNum(expectedCryptoAllowance.getOwner()))
                .setSpender(AccountID.newBuilder().setAccountNum(expectedCryptoAllowance.getSpender())));

        // duplicate nft allowance by serial
        builder.addNftAllowances(com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
                .setOwner(AccountID.newBuilder()
                        .setAccountNum(expectedNft.getAccountId().getEntityNum()))
                .addSerialNumbers(expectedNft.getId().getSerialNumber())
                .setSpender(AccountID.newBuilder()
                        .setAccountNum(expectedNft.getSpender().getEntityNum() + 1))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedNft.getTokenId())));
        // duplicate nft approved for all allowance, approved for all flag is flipped from the last one
        builder.addNftAllowances(com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(!expectedNftAllowance.isApprovedForAll()))
                .setOwner(AccountID.newBuilder()
                        .setAccountNum(expectedNft.getAccountId().getEntityNum()))
                .addSerialNumbers(expectedNft.getId().getSerialNumber())
                .setSpender(AccountID.newBuilder()
                        .setAccountNum(expectedNft.getSpender().getEntityNum()))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedNft.getTokenId())));
        // the last one is honored
        builder.addNftAllowances(com.hederahashgraph.api.proto.java.NftAllowance.newBuilder()
                .setApprovedForAll(BoolValue.of(expectedNftAllowance.isApprovedForAll()))
                .setOwner(AccountID.newBuilder()
                        .setAccountNum(expectedNft.getAccountId().getEntityNum()))
                .addSerialNumbers(expectedNft.getId().getSerialNumber())
                .setSpender(AccountID.newBuilder()
                        .setAccountNum(expectedNft.getSpender().getEntityNum()))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedNft.getTokenId())));

        // duplicate token allowance
        builder.addTokenAllowances(com.hederahashgraph.api.proto.java.TokenAllowance.newBuilder()
                .setAmount(expectedTokenAllowance.getAmount() - 10)
                .setOwner(AccountID.newBuilder().setAccountNum(expectedTokenAllowance.getOwner()))
                .setSpender(AccountID.newBuilder().setAccountNum(expectedTokenAllowance.getSpender()))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedTokenAllowance.getTokenId())));
        // the last one is honored
        builder.addTokenAllowances(com.hederahashgraph.api.proto.java.TokenAllowance.newBuilder()
                .setAmount(expectedTokenAllowance.getAmount())
                .setOwner(AccountID.newBuilder().setAccountNum(expectedTokenAllowance.getOwner()))
                .setSpender(AccountID.newBuilder().setAccountNum(expectedTokenAllowance.getSpender()))
                .setTokenId(TokenID.newBuilder().setTokenNum(expectedTokenAllowance.getTokenId())));
    }

    private void setTransactionPayer(TransactionBody.Builder builder) {
        builder.getTransactionIDBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(payerAccountId.getEntityNum()));
    }
}
