/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.crypto.validators;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.BoolValue;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
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
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApproveAllowanceChecksTest {

    @Mock
    private Account owner;

    @Mock
    private Account treasury;

    @Mock
    private Account payerAccount;

    @Mock
    private Store store;

    @Mock
    private Token merkleTokenFungible;

    @Mock
    private Token merkleTokenNFT;

    @Mock
    private UniqueToken uniqueToken;

    ApproveAllowanceChecks subject;

    private final AccountID spender1 = asAccount("0.0.123");
    private final AccountID spender2 = asAccount("0.0.1234");
    private final TokenID token1 = asToken("0.0.100");
    private final TokenID token2 = asToken("0.0.200");
    private final AccountID ownerId1 = asAccount("0.0.5000");
    private final AccountID ownerId2 = asAccount("0.0.5001");
    private final AccountID payer = asAccount("0.0.3000");
    private final Id tokenId1 = Id.fromGrpcToken(token1);
    private final Id tokenId2 = Id.fromGrpcToken(token2);

    private Token token1Model = new Token(tokenId1);
    private Token token2Model = new Token(tokenId2);

    private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder()
            .setSpender(spender1)
            .setAmount(10L)
            .setOwner(ownerId1)
            .build();
    private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder()
            .setSpender(spender1)
            .setAmount(10L)
            .setTokenId(token1)
            .setOwner(ownerId1)
            .build();
    private final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
            .setSpender(spender1)
            .setOwner(ownerId1)
            .setTokenId(token2)
            .setApprovedForAll(BoolValue.of(false))
            .addAllSerialNumbers(List.of(1L, 10L))
            .build();
    private final NftAllowance nftAllowance2 = NftAllowance.newBuilder()
            .setSpender(spender1)
            .setOwner(ownerId2)
            .setTokenId(token2)
            .setApprovedForAll(BoolValue.of(false))
            .addAllSerialNumbers(List.of(1L, 10L))
            .build();
    private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
    private List<TokenAllowance> tokenAllowances = new ArrayList<>();
    private List<NftAllowance> nftAllowances = new ArrayList<>();

    private TransactionBody cryptoApproveAllowanceTxn;
    private CryptoApproveAllowanceTransactionBody op;

    @BeforeEach
    void setUp() {
        token1Model = token1Model.setMaxSupply(5000L);
        token2Model = token2Model.setMaxSupply(5000L);

        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);

        subject = new ApproveAllowanceChecks();
    }

    private void setUpForTest() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(store.loadPossiblyPausedToken(tokenId1.asEvmAddress())).willReturn(merkleTokenFungible);
        given(store.loadPossiblyPausedToken(tokenId2.asEvmAddress())).willReturn(merkleTokenNFT);
        given(merkleTokenFungible.isFungibleCommon()).willReturn(true);
        given(merkleTokenNFT.isFungibleCommon()).willReturn(false);
        given(merkleTokenFungible.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(merkleTokenFungible.getMaxSupply()).willReturn(500L);
        given(merkleTokenFungible.getId()).willReturn(tokenId1);
        given(merkleTokenNFT.getId()).willReturn(tokenId2);
        given(store.hasAssociation(new TokenRelationshipKey(tokenId1.asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);
        given(store.hasAssociation(new TokenRelationshipKey(tokenId2.asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);
    }

    @Test
    void returnsValidationOnceFailed() {
        for (int i = 0; i < 20; i++) {
            cryptoAllowances.add(cryptoAllowance1);
            tokenAllowances.add(tokenAllowance1);
            nftAllowances.add(nftAllowance1);
        }

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .build())
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        assertEquals(
                MAX_ALLOWANCES_EXCEEDED,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        owner,
                        store));

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllTokenAllowances(tokenAllowances)
                        .build())
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        assertEquals(
                MAX_ALLOWANCES_EXCEEDED,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        owner,
                        store));

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllNftAllowances(nftAllowances)
                        .build())
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        assertEquals(
                MAX_ALLOWANCES_EXCEEDED,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        owner,
                        store));
    }

    @Test
    void succeedsWithEmptyLists() {
        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(
                        CryptoApproveAllowanceTransactionBody.newBuilder().build())
                .build();
        assertEquals(
                OK,
                subject.validateCryptoAllowances(
                        cryptoApproveAllowanceTxn.getCryptoApproveAllowance().getCryptoAllowancesList(), owner, store));
        assertEquals(
                OK,
                subject.validateFungibleTokenAllowances(
                        cryptoApproveAllowanceTxn.getCryptoApproveAllowance().getTokenAllowancesList(), owner, store));
        assertEquals(
                OK,
                subject.validateNftAllowances(
                        cryptoApproveAllowanceTxn.getCryptoApproveAllowance().getNftAllowancesList(), owner, store));
    }

    @Test
    void validatesBasicsAsExpected() {
        setUpForTest();

        final var badCryptoAllowance = CryptoAllowance.newBuilder()
                .setSpender(ownerId1)
                .setOwner(ownerId1)
                .setAmount(10L)
                .build();
        final var okErcAllowance = TokenAllowance.newBuilder()
                .setSpender(ownerId1)
                .setOwner(ownerId1)
                .setAmount(20L)
                .setTokenId(token1)
                .build();
        final var badNftAllowance = NftAllowance.newBuilder()
                .setSpender(ownerId1)
                .setTokenId(token2)
                .setApprovedForAll(BoolValue.of(false))
                .setOwner(ownerId1)
                .addAllSerialNumbers(List.of(1L))
                .build();

        cryptoAllowances.add(badCryptoAllowance);
        assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateCryptoAllowances(cryptoAllowances, owner, store));

        tokenAllowances.add(okErcAllowance);
        assertEquals(OK, subject.validateFungibleTokenAllowances(tokenAllowances, owner, store));

        nftAllowances.add(badNftAllowance);
        assertEquals(SPENDER_ACCOUNT_SAME_AS_OWNER, subject.validateNftAllowances(nftAllowances, owner, store));
    }

    @Test
    void validateNegativeAmounts() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(store.loadPossiblyPausedToken(tokenId1.asEvmAddress())).willReturn(merkleTokenFungible);
        given(merkleTokenFungible.isFungibleCommon()).willReturn(true);
        given(merkleTokenFungible.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(merkleTokenFungible.getMaxSupply()).willReturn(500L);
        given(merkleTokenFungible.getId()).willReturn(tokenId1);
        given(store.hasAssociation(new TokenRelationshipKey(tokenId1.asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);

        final var badCryptoAllowance = CryptoAllowance.newBuilder()
                .setSpender(spender2)
                .setAmount(-10L)
                .setOwner(ownerId1)
                .build();
        final var badTokenAllowance = TokenAllowance.newBuilder()
                .setSpender(spender2)
                .setAmount(-20L)
                .setTokenId(token1)
                .setOwner(ownerId1)
                .build();

        cryptoAllowances.add(badCryptoAllowance);
        assertEquals(NEGATIVE_ALLOWANCE_AMOUNT, subject.validateCryptoAllowances(cryptoAllowances, owner, store));

        tokenAllowances.add(badTokenAllowance);
        assertEquals(NEGATIVE_ALLOWANCE_AMOUNT, subject.validateFungibleTokenAllowances(tokenAllowances, owner, store));
    }

    @Test
    void failsWhenExceedsMaxTokenSupply() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(store.loadPossiblyPausedToken(tokenId1.asEvmAddress())).willReturn(merkleTokenFungible);
        given(merkleTokenFungible.isFungibleCommon()).willReturn(true);
        given(merkleTokenFungible.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(merkleTokenFungible.getMaxSupply()).willReturn(500L);
        given(merkleTokenFungible.getId()).willReturn(tokenId1);
        given(store.hasAssociation(new TokenRelationshipKey(tokenId1.asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);

        final var badTokenAllowance = TokenAllowance.newBuilder()
                .setSpender(spender2)
                .setAmount(100000L)
                .setTokenId(token1)
                .setOwner(ownerId1)
                .build();

        tokenAllowances.add(badTokenAllowance);
        assertEquals(
                AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY,
                subject.validateFungibleTokenAllowances(tokenAllowances, owner, store));
    }

    @Test
    void failsForNftInFungibleTokenAllowances() {
        given(store.loadPossiblyPausedToken(token1Model.getId().asEvmAddress())).willReturn(token1Model);
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        final var badTokenAllowance = TokenAllowance.newBuilder()
                .setSpender(spender2)
                .setAmount(100000L)
                .setTokenId(token2)
                .setOwner(ownerId1)
                .build();

        tokenAllowances.add(badTokenAllowance);
        assertEquals(
                NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES,
                subject.validateFungibleTokenAllowances(tokenAllowances, owner, store));
    }

    @Test
    void returnsInvalidOwnerId() {

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .build())
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        given(payerAccount.getId()).willReturn(Id.fromGrpcAccount(payer));
        given(store.getAccount(asTypedEvmAddress(ownerId1), OnMissing.THROW))
                .willThrow(InvalidTransactionException.class);
        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        payerAccount,
                        store));

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllTokenAllowances(tokenAllowances)
                        .build())
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        payerAccount,
                        store));

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllNftAllowances(nftAllowances)
                        .build())
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        given(store.loadPossiblyPausedToken(asTypedEvmAddress(token2))).willReturn(token2Model);

        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        payerAccount,
                        store));
    }

    @Test
    void cannotGrantApproveForAllUsingDelegatingSpender() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(store.loadPossiblyPausedToken(token2Model.getId().asEvmAddress())).willReturn(token2Model);
        given(store.hasAssociation(
                        new TokenRelationshipKey(token2Model.getId().asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);

        final var badNftAllowance = NftAllowance.newBuilder()
                .setSpender(spender2)
                .addAllSerialNumbers(List.of(1L))
                .setTokenId(token2)
                .setOwner(ownerId1)
                .setDelegatingSpender(spender1)
                .setApprovedForAll(BoolValue.of(true))
                .build();

        nftAllowances.clear();
        nftAllowances.add(badNftAllowance);

        assertEquals(
                DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL,
                subject.validateNftAllowances(nftAllowances, owner, store));
    }

    @Test
    void cannotGrantExplicitNftAllowanceUsingDelegatingSpenderWithNoApproveForAllAllowance() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(store.loadPossiblyPausedToken(token2Model.getId().asEvmAddress())).willReturn(token2Model);
        given(store.hasAssociation(
                        new TokenRelationshipKey(token2Model.getId().asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);
        given(owner.getApproveForAllNfts()).willReturn(new TreeSet<>());

        final var badNftAllowance = NftAllowance.newBuilder()
                .setSpender(spender2)
                .addAllSerialNumbers(List.of(1L))
                .setTokenId(token2)
                .setOwner(ownerId1)
                .setDelegatingSpender(spender1)
                .setApprovedForAll(BoolValue.of(false))
                .build();

        nftAllowances.clear();
        nftAllowances.add(badNftAllowance);

        assertEquals(
                DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL,
                subject.validateNftAllowances(nftAllowances, owner, store));
    }

    @Test
    void canGrantExplicitNftAllowanceUsingDelegatingSpenderWithApproveForAllAllowance() {
        final var allowanceKey =
                FcTokenAllowanceId.from(EntityNum.fromTokenId(token2), EntityNum.fromAccountId(spender1));

        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(store.loadPossiblyPausedToken(token2Model.getId().asEvmAddress())).willReturn(token2Model);
        given(store.hasAssociation(
                        new TokenRelationshipKey(token2Model.getId().asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);
        final var sortedSet = new TreeSet<FcTokenAllowanceId>();
        sortedSet.add(allowanceKey);
        given(owner.getApproveForAllNfts()).willReturn(sortedSet);

        final var badNftAllowance = NftAllowance.newBuilder()
                .setSpender(spender2)
                .addAllSerialNumbers(List.of(1L))
                .setTokenId(token2)
                .setOwner(ownerId1)
                .setDelegatingSpender(spender1)
                .setApprovedForAll(BoolValue.of(false))
                .build();

        nftAllowances.clear();
        nftAllowances.add(badNftAllowance);

        assertEquals(OK, subject.validateNftAllowances(nftAllowances, owner, store));
    }

    @Test
    void failsWhenTokenNotAssociatedToAccount() {
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(store.loadPossiblyPausedToken(tokenId1.asEvmAddress())).willReturn(merkleTokenFungible);
        given(merkleTokenFungible.isFungibleCommon()).willReturn(true);
        given(merkleTokenFungible.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(merkleTokenFungible.getMaxSupply()).willReturn(500L);
        given(merkleTokenFungible.getId()).willReturn(tokenId1);
        given(store.hasAssociation(new TokenRelationshipKey(tokenId1.asEvmAddress(), owner.getAccountAddress())))
                .willReturn(false);
        assertEquals(
                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
                subject.validateFungibleTokenAllowances(tokenAllowances, owner, store));
    }

    @Test
    void happyPath() {
        setUpForTest();
        getValidTxnCtx();

        assertEquals(
                OK,
                subject.allowancesValidation(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        owner,
                        store));
    }

    @Test
    void fungibleInNFTAllowances() {
        given(store.loadPossiblyPausedToken(tokenId1.asEvmAddress())).willReturn(merkleTokenFungible);
        given(store.loadPossiblyPausedToken(tokenId2.asEvmAddress())).willReturn(merkleTokenNFT);
        given(merkleTokenFungible.isFungibleCommon()).willReturn(true);
        given(merkleTokenFungible.isFungibleCommon()).willReturn(true);
        given(merkleTokenNFT.isFungibleCommon()).willReturn(false);
        given(merkleTokenNFT.getId()).willReturn(tokenId2);
        given(merkleTokenNFT.isFungibleCommon()).willReturn(false);
        given(store.hasAssociation(
                        new TokenRelationshipKey(token2Model.getId().asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        final NftId nftId1 = new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), 10L);
        final NftId nftId2 = new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), 1L);
        given(store.getUniqueToken(nftId1, OnMissing.THROW)).willReturn(uniqueToken);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken);

        final var badNftAllowance = NftAllowance.newBuilder()
                .setSpender(spender2)
                .addAllSerialNumbers(List.of(1L))
                .setTokenId(token1)
                .setOwner(ownerId1)
                .setApprovedForAll(BoolValue.of(false))
                .build();

        nftAllowances.add(badNftAllowance);
        assertEquals(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES, subject.validateNftAllowances(nftAllowances, owner, store));
    }

    @Test
    void validateSerialsExistence() {
        final var serials = List.of(1L, 10L);
        final NftId nftId = new NftId(tokenId1.shard(), tokenId1.realm(), tokenId2.num(), 1L);
        given(store.getUniqueToken(nftId, OnMissing.THROW)).willThrow(InvalidTransactionException.class);

        var validity = subject.validateSerialNums(serials, token2Model, store);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void validateInvalidSerials() {
        final var serials = List.of(-1L, 10L);
        var validity = subject.validateSerialNums(serials, token2Model, store);
        assertEquals(INVALID_TOKEN_NFT_SERIAL_NUMBER, validity);
    }

    @Test
    void approvesAllowanceFromTreasury() {
        final var serials = List.of(1L);
        token2Model = token2Model.setTreasury(treasury);
        final NftId nftId = new NftId(tokenId2.shard(), tokenId2.realm(), tokenId2.num(), 1L);
        given(store.getUniqueToken(nftId, OnMissing.THROW)).willReturn(uniqueToken);

        var validity = subject.validateSerialNums(serials, token2Model, store);
        assertEquals(OK, validity);
    }

    @Test
    void validateRepeatedSerials() {
        final var serials = List.of(1L, 10L, 1L);
        var validity = subject.validateSerialNums(serials, token2Model, store);
        assertEquals(OK, validity);
    }

    @Test
    void semanticCheckForEmptyAllowancesInOp() {
        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder())
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        assertEquals(
                EMPTY_ALLOWANCES,
                subject.validateAllowanceCount(
                        op.getCryptoAllowancesList(), op.getTokenAllowancesList(), op.getNftAllowancesList()));
    }

    @Test
    void loadsOwnerAccountNotDefaultingToPayer() {
        given(store.loadPossiblyPausedToken(tokenId1.asEvmAddress())).willReturn(merkleTokenFungible);
        given(merkleTokenFungible.isFungibleCommon()).willReturn(true);
        given(merkleTokenFungible.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(merkleTokenFungible.getMaxSupply()).willReturn(500L);
        given(merkleTokenFungible.getId()).willReturn(tokenId1);
        given(store.hasAssociation(new TokenRelationshipKey(tokenId1.asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);
        given(store.getAccount(asTypedEvmAddress(ownerId1), OnMissing.THROW)).willReturn(owner);
        getValidTxnCtx();

        assertEquals(OK, subject.validateFungibleTokenAllowances(op.getTokenAllowancesList(), payerAccount, store));
        verify(store).getAccount(asTypedEvmAddress(ownerId1), OnMissing.THROW);

        given(store.getAccount(asTypedEvmAddress(ownerId1), OnMissing.THROW))
                .willThrow(InvalidTransactionException.class);

        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.validateFungibleTokenAllowances(op.getTokenAllowancesList(), payerAccount, store));
    }

    @Test
    void loadsOwnerAccountInNftNotDefaultingToPayer() {
        given(store.loadPossiblyPausedToken(tokenId2.asEvmAddress())).willReturn(merkleTokenNFT);
        given(merkleTokenNFT.isFungibleCommon()).willReturn(false);
        given(merkleTokenNFT.getId()).willReturn(tokenId1);
        given(store.hasAssociation(new TokenRelationshipKey(tokenId1.asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);
        given(store.getAccount(asTypedEvmAddress(ownerId1), OnMissing.THROW)).willReturn(owner);
        final NftId nftId1 = new NftId(tokenId2.shard(), tokenId2.realm(), tokenId1.num(), 1L);
        final NftId nftId2 = new NftId(tokenId2.shard(), tokenId2.realm(), tokenId1.num(), 10L);
        given(store.getUniqueToken(nftId1, OnMissing.THROW)).willReturn(uniqueToken);
        given(store.getUniqueToken(nftId2, OnMissing.THROW)).willReturn(uniqueToken);
        given(owner.getId()).willReturn(Id.fromGrpcAccount(ownerId1));
        given(store.getAccount(asTypedEvmAddress(ownerId1), OnMissing.THROW)).willReturn(owner);

        getValidTxnCtx();

        assertEquals(OK, subject.validateNftAllowances(op.getNftAllowancesList(), payerAccount, store));
        verify(store).getAccount(asTypedEvmAddress(ownerId1), OnMissing.THROW);

        given(store.getAccount(asTypedEvmAddress(ownerId1), OnMissing.THROW))
                .willThrow(InvalidTransactionException.class);
        assertEquals(
                INVALID_ALLOWANCE_OWNER_ID,
                subject.validateNftAllowances(op.getNftAllowancesList(), payerAccount, store));
        verify(store, times(2)).getAccount(asTypedEvmAddress(ownerId1), OnMissing.THROW);
    }

    @Test
    void missingOwnerDefaultsToPayer() {
        given(store.loadPossiblyPausedToken(tokenId1.asEvmAddress())).willReturn(merkleTokenFungible);
        given(store.loadPossiblyPausedToken(tokenId2.asEvmAddress())).willReturn(merkleTokenNFT);
        given(merkleTokenFungible.isFungibleCommon()).willReturn(true);
        given(merkleTokenNFT.isFungibleCommon()).willReturn(false);
        given(merkleTokenFungible.getSupplyType()).willReturn(TokenSupplyType.FINITE);
        given(merkleTokenFungible.getMaxSupply()).willReturn(500L);
        given(merkleTokenFungible.getId()).willReturn(tokenId1);
        given(merkleTokenNFT.getId()).willReturn(tokenId2);
        given(payerAccount.getId()).willReturn(Id.fromGrpcAccount(payer));
        given(store.hasAssociation(new TokenRelationshipKey(tokenId1.asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);
        given(store.hasAssociation(new TokenRelationshipKey(tokenId2.asEvmAddress(), owner.getAccountAddress())))
                .willReturn(true);

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
                .setApprovedForAll(BoolValue.of(false))
                .addAllSerialNumbers(List.of(1L, 10L))
                .build();

        cryptoAllowances.clear();
        tokenAllowances.clear();
        nftAllowances.clear();
        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(
                        CryptoApproveAllowanceTransactionBody.newBuilder().addAllCryptoAllowances(cryptoAllowances))
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        assertEquals(
                OK,
                subject.validateCryptoAllowances(
                        cryptoApproveAllowanceTxn.getCryptoApproveAllowance().getCryptoAllowancesList(),
                        payerAccount,
                        store));
        verify(store, never()).getAccount(any(), any());

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(
                        CryptoApproveAllowanceTransactionBody.newBuilder().addAllTokenAllowances(tokenAllowances))
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        assertEquals(
                OK,
                subject.validateFungibleTokenAllowances(
                        cryptoApproveAllowanceTxn.getCryptoApproveAllowance().getTokenAllowancesList(),
                        payerAccount,
                        store));
        verify(store, never()).getAccount(any(), any());

        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(
                        CryptoApproveAllowanceTransactionBody.newBuilder().addAllNftAllowances(nftAllowances))
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
        assertEquals(
                OK,
                subject.validateNftAllowances(
                        cryptoApproveAllowanceTxn.getCryptoApproveAllowance().getNftAllowancesList(),
                        payerAccount,
                        store));
        verify(store, never()).getAccount(any(), any());
    }

    private void getValidTxnCtx() {
        cryptoApproveAllowanceTxn = TransactionBody.newBuilder()
                .setTransactionID(ourTxnId())
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances)
                        .build())
                .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payer)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
                .build();
    }
}
