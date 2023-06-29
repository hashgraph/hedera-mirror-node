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

package com.hedera.services.txns.crypto;

import static com.hedera.services.store.models.Id.fromGrpcAccount;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.EntityNum.fromTokenId;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.BoolValue;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApproveAllowanceLogicTest {
    @Mock
    private Store store;

    private TransactionBody cryptoApproveAllowanceTxn;
    private CryptoApproveAllowanceTransactionBody op;

    ApproveAllowanceLogic subject;

    private static final long serial1 = 1L;
    private static final long serial2 = 10L;
    private static final AccountID spender1 = asAccount("0.0.123");
    private static final TokenID token1 = asToken("0.0.100");
    private static final TokenID token2 = asToken("0.0.200");
    private static final AccountID payerId = asAccount("0.0.5000");
    private static final AccountID ownerId = asAccount("0.0.6000");
    private static final Id tokenId1 = Id.fromGrpcToken(token1);
    private static final Id tokenId2 = Id.fromGrpcToken(token2);
    private static final Id spenderId1 = fromGrpcAccount(spender1);
    private static final Instant consensusTime = Instant.now();
    private Token token1Model = new Token(Id.fromGrpcToken(token1));
    private Token token2Model = new Token(Id.fromGrpcToken(token2));
    private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder()
            .setSpender(spender1)
            .setOwner(ownerId)
            .setAmount(10L)
            .build();
    private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder()
            .setSpender(spender1)
            .setAmount(10L)
            .setTokenId(token1)
            .setOwner(ownerId)
            .build();
    private final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
            .setSpender(spender1)
            .setOwner(ownerId)
            .setTokenId(token2)
            .setApprovedForAll(BoolValue.of(true))
            .addAllSerialNumbers(List.of(serial1, serial2))
            .build();
    private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
    private List<TokenAllowance> tokenAllowances = new ArrayList<>();
    private List<NftAllowance> nftAllowances = new ArrayList<>();
    private Account payerAccount = new Account(fromGrpcAccount(payerId), 0L);
    private Account ownerAccount = new Account(fromGrpcAccount(ownerId), 0L);
    private UniqueToken nft1 = new UniqueToken(tokenId1, serial1, null, fromGrpcAccount(ownerId), null, null);
    private UniqueToken nft2 = new UniqueToken(tokenId2, serial2, null, fromGrpcAccount(ownerId), null, null);

    @BeforeEach
    void setup() {
        subject = new ApproveAllowanceLogic(store);
    }

    @Test
    void happyPathAddsAllowances() {
        givenValidTxnCtx();

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        given(store.getAccount(ownerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(ownerAccount);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);

        subject.approveAllowance(
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(payerId).asGrpcAccount());

        assertEquals(1, ownerAccount.getCryptoAllowances().size());
        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(1, ownerAccount.getApproveForAllNfts().size());

        // verify(accountStore).commitAccount(ownerAccount);
    }

    @Test
    void considersPayerAsOwnerIfNotMentioned() {
        givenValidTxnCtxWithOwnerAsPayer();
        nft1 = new UniqueToken(tokenId1, serial1, null, fromGrpcAccount(payerId), null, null);
        nft2 = new UniqueToken(tokenId2, serial2, null, fromGrpcAccount(payerId), null, null);

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);

        assertEquals(0, payerAccount.getCryptoAllowances().size());
        assertEquals(0, payerAccount.getFungibleTokenAllowances().size());
        assertEquals(0, payerAccount.getApproveForAllNfts().size());

        subject.approveAllowance(
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(payerId).asGrpcAccount());

        assertEquals(1, payerAccount.getCryptoAllowances().size());
        assertEquals(1, payerAccount.getFungibleTokenAllowances().size());
        assertEquals(1, payerAccount.getApproveForAllNfts().size());
        assertEquals(spenderId1, subject.getNftsTouched().get(nft1.getNftId()).getSpender());
        assertEquals(spenderId1, subject.getNftsTouched().get(nft2.getNftId()).getSpender());

        // verify(store).commitAccount(payerAccount);
    }

    @Test
    void wipesSerialsWhenApprovedForAll() {
        givenValidTxnCtx();

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        given(store.getAccount(ownerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(ownerAccount);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);

        subject.approveAllowance(
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(payerId).asGrpcAccount());

        assertEquals(1, ownerAccount.getCryptoAllowances().size());
        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(1, ownerAccount.getApproveForAllNfts().size());
    }

    @Test
    void emptyAllowancesInStateTransitionWorks() {
        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder())
                .build();

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        subject.approveAllowance(
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(payerId).asGrpcAccount());

        assertEquals(0, ownerAccount.getCryptoAllowances().size());
        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(0, ownerAccount.getApproveForAllNfts().size());
    }

    @Test
    void doesntAddAllowancesWhenAmountIsZero() {
        givenTxnCtxWithZeroAmount();

        given(store.getAccount(ownerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(ownerAccount);
        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);

        subject.approveAllowance(
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(payerId).asGrpcAccount());

        assertEquals(0, ownerAccount.getCryptoAllowances().size());
        assertEquals(0, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(0, ownerAccount.getApproveForAllNfts().size());
    }

    @Test
    void skipsTxnWhenKeyExistsAndAmountGreaterThanZero() {
        var ownerAccount = new Account(fromGrpcAccount(ownerId), 0L);
        ownerAccount = setUpOwnerWithExistingKeys(ownerAccount);

        assertEquals(1, ownerAccount.getCryptoAllowances().size());
        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(1, ownerAccount.getApproveForAllNfts().size());

        givenValidTxnCtx();

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        given(store.getAccount(ownerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(ownerAccount);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);

        subject.approveAllowance(
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(payerId).asGrpcAccount());

        assertEquals(1, ownerAccount.getCryptoAllowances().size());
        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(1, ownerAccount.getApproveForAllNfts().size());
    }

    @Test
    void checkIfApproveForAllIsSet() {
        final NftAllowance nftAllowance = NftAllowance.newBuilder()
                .setSpender(spender1)
                .setOwner(ownerId)
                .setTokenId(token2)
                .addAllSerialNumbers(List.of(serial1))
                .build();
        final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
                .setSpender(spender1)
                .setOwner(ownerId)
                .setTokenId(token2)
                .setApprovedForAll(BoolValue.of(false))
                .addAllSerialNumbers(List.of(serial1))
                .build();
        nftAllowances.add(nftAllowance);
        nftAllowances.add(nftAllowance1);

        var ownerAcccount = new Account(fromGrpcAccount(ownerId), 0L);

        givenValidTxnCtx();

        given(store.getAccount(spenderId1.asEvmAddress(), OnMissing.THROW)).willReturn(payerAccount);
        ownerAcccount.setCryptoAllowances(new TreeMap<>());
        ownerAcccount.setFungibleTokenAllowances(new TreeMap<>());
        ownerAcccount.setApproveForAllNfts(new TreeSet<>());
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);

        subject.applyNftAllowances(nftAllowances, ownerAcccount);

        assertEquals(1, ownerAcccount.getApproveForAllNfts().size());
    }

    @Test
    void overridesExistingAllowances() {
        givenValidTxnCtxForOverwritingAllowances();
        addExistingAllowances();

        assertEquals(1, ownerAccount.getCryptoAllowances().size());
        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(2, ownerAccount.getApproveForAllNfts().size());
        assertEquals(
                20,
                ownerAccount.getCryptoAllowances().get(fromAccountId(spender1)).intValue());
        assertEquals(
                20,
                ownerAccount
                        .getFungibleTokenAllowances()
                        .get(FcTokenAllowanceId.from(fromTokenId(token1), fromAccountId(spender1)))
                        .intValue());
        assertTrue(ownerAccount
                .getApproveForAllNfts()
                .contains(FcTokenAllowanceId.from(fromTokenId(token1), fromAccountId(spender1))));
        assertTrue(ownerAccount
                .getApproveForAllNfts()
                .contains(FcTokenAllowanceId.from(fromTokenId(token1), fromAccountId(spender1))));

        given(store.getAccount(payerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(payerAccount);
        given(store.getAccount(ownerAccount.getAccountAddress(), OnMissing.THROW))
                .willReturn(ownerAccount);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial1), OnMissing.THROW))
                .willReturn(nft1);
        given(store.getUniqueToken(
                        new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), serial2), OnMissing.THROW))
                .willReturn(nft2);

        subject.approveAllowance(
                op.getCryptoAllowancesList(),
                op.getTokenAllowancesList(),
                op.getNftAllowancesList(),
                fromGrpcAccount(payerId).asGrpcAccount());

        assertEquals(1, ownerAccount.getCryptoAllowances().size());
        assertEquals(1, ownerAccount.getFungibleTokenAllowances().size());
        assertEquals(1, ownerAccount.getApproveForAllNfts().size());
        assertEquals(
                10,
                ownerAccount.getCryptoAllowances().get(fromAccountId(spender1)).intValue());
        assertEquals(
                10,
                ownerAccount
                        .getFungibleTokenAllowances()
                        .get(FcTokenAllowanceId.from(fromTokenId(token1), fromAccountId(spender1)))
                        .intValue());
        assertTrue(ownerAccount
                .getApproveForAllNfts()
                .contains(FcTokenAllowanceId.from(fromTokenId(token1), fromAccountId(spender1))));
        assertFalse(ownerAccount
                .getApproveForAllNfts()
                .contains(FcTokenAllowanceId.from(fromTokenId(token2), fromAccountId(spender1))));
    }

    private void addExistingAllowances() {
        final SortedMap<EntityNum, Long> existingCryptoAllowances = new TreeMap<>();
        final SortedMap<FcTokenAllowanceId, Long> existingTokenAllowances = new TreeMap<>();
        final SortedSet<FcTokenAllowanceId> existingNftAllowances = new TreeSet<>();

        existingCryptoAllowances.put(fromAccountId(spender1), 20L);
        existingTokenAllowances.put(FcTokenAllowanceId.from(fromTokenId(token1), fromAccountId(spender1)), 20L);
        existingNftAllowances.add(FcTokenAllowanceId.from(fromTokenId(token2), fromAccountId(spender1)));
        existingNftAllowances.add(FcTokenAllowanceId.from(fromTokenId(token1), fromAccountId(spender1)));
        ownerAccount = ownerAccount
                .setCryptoAllowances(existingCryptoAllowances)
                .setFungibleTokenAllowances(existingTokenAllowances)
                .setApproveForAllNfts(existingNftAllowances);
    }

    private void givenValidTxnCtxForOverwritingAllowances() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);
        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final NftAllowance nftAllowance2 = NftAllowance.newBuilder()
                .setSpender(spender1)
                .setOwner(ownerId)
                .setTokenId(token2)
                .setApprovedForAll(BoolValue.of(false))
                .addAllSerialNumbers(List.of(serial1, serial2))
                .build();

        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);
        nftAllowances.add(nftAllowance2);

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances))
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        ownerAccount = ownerAccount
                .setApproveForAllNfts(new TreeSet<>())
                .setCryptoAllowances(new TreeMap<>())
                .setFungibleTokenAllowances(new TreeMap<>());
    }

    private void givenValidTxnCtx() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);
        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances))
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        ownerAccount = ownerAccount
                .setApproveForAllNfts(new TreeSet<>())
                .setCryptoAllowances(new TreeMap<>())
                .setFungibleTokenAllowances(new TreeMap<>());
    }

    private void givenTxnCtxWithZeroAmount() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);
        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder()
                .setOwner(ownerId)
                .setSpender(spender1)
                .setAmount(0L)
                .build();
        final TokenAllowance tokenAllowance = TokenAllowance.newBuilder()
                .setSpender(spender1)
                .setAmount(0L)
                .setTokenId(token1)
                .setOwner(ownerId)
                .build();
        final NftAllowance nftAllowance = NftAllowance.newBuilder()
                .setSpender(spender1)
                .setTokenId(token2)
                .setApprovedForAll(BoolValue.of(false))
                .setOwner(ownerId)
                .addAllSerialNumbers(List.of(1L, 10L))
                .build();

        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);
        cryptoAllowances.add(cryptoAllowance);
        tokenAllowances.add(tokenAllowance);
        nftAllowances.add(nftAllowance);

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances))
                .build();

        ownerAccount = ownerAccount
                .setApproveForAllNfts(new TreeSet<>())
                .setCryptoAllowances(new TreeMap<>())
                .setFungibleTokenAllowances(new TreeMap<>());
    }

    private void givenValidTxnCtxWithOwnerAsPayer() {
        token1Model = token1Model.setMaxSupply(5000L).setType(TokenType.FUNGIBLE_COMMON);
        token2Model = token2Model.setMaxSupply(5000L).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        final CryptoAllowance cryptoAllowance1 =
                CryptoAllowance.newBuilder().setSpender(spender1).setAmount(10L).build();
        final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder()
                .setSpender(spender1)
                .setAmount(10L)
                .setTokenId(token1)
                .build();
        final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
                .setSpender(spender1)
                .setTokenId(token2)
                .setApprovedForAll(BoolValue.of(true))
                .addAllSerialNumbers(List.of(1L, 10L))
                .build();

        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances))
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        payerAccount = payerAccount
                .setApproveForAllNfts(new TreeSet<>())
                .setCryptoAllowances(new TreeMap<>())
                .setFungibleTokenAllowances(new TreeMap<>());
    }

    private Account setUpOwnerWithExistingKeys(Account ownerAccount) {
        SortedMap<EntityNum, Long> cryptoAllowances = new TreeMap<>();
        SortedMap<FcTokenAllowanceId, Long> tokenAllowances = new TreeMap<>();
        SortedSet<FcTokenAllowanceId> nftAllowances = new TreeSet<>();
        final var id = FcTokenAllowanceId.from(fromTokenId(token1), fromAccountId(spender1));
        final var Nftid = FcTokenAllowanceId.from(fromTokenId(token2), fromAccountId(spender1));
        cryptoAllowances.put(fromAccountId(spender1), 10000L);
        tokenAllowances.put(id, 100000L);
        nftAllowances.add(Nftid);
        return ownerAccount
                .setApproveForAllNfts(nftAllowances)
                .setCryptoAllowances(cryptoAllowances)
                .setFungibleTokenAllowances(tokenAllowances);
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payerId)
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
                .build();
    }
}
