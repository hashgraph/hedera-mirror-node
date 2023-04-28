/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.store.models;

import static com.hedera.services.utils.BitPackUtils.MAX_NUM_ALLOWED;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_METADATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SERIAL_NUMBER_LIMIT_REACHED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TREASURY_MUST_OWN_BURNED_NFT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.state.submerkle.RichInstant;

class TokenTest {
    private final JKey someKey = TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked();
    private final int numAssociations = 2;
    private final int numPositiveBalances = 1;
    private final long initialSupply = 1_000L;
    private final long initialTreasuryBalance = 500L;
    private final Id tokenId = new Id(1, 2, 3);
    private final Id treasuryId = new Id(0, 0, 0);
    private final Id nonTreasuryId = new Id(3, 2, 3);
    private final Account treasuryAccount = new Account(treasuryId);
    private final Account nonTreasuryAccount = new Account(nonTreasuryId);
    private final EntityNumPair treasuryAssociationKey = EntityNumPair.fromLongs(treasuryId.num(), tokenId.num());
    private final EntityNumPair nonTreasuryAssociationKey = EntityNumPair.fromLongs(nonTreasuryId.num(), tokenId.num());

    private Token subject;

    @BeforeEach
    void setUp() {
        subject = new Token(tokenId, false, TokenType.FUNGIBLE_COMMON, TokenSupplyType.FINITE, initialSupply,
                21_000_000, null, null, null, null, null, null, null, false, treasuryAccount, new Account(Id.DEFAULT)
                , false, false, 1000L, true, "the mother", "bitcoin", "BTC", 10, 0L, 1, null);

    }

    @Test
    void deleteAsExpected() {
        assertDoesNotThrow(() -> subject.delete());
    }

    @Test
    void constructsOkToken() {
        final var feeScheduleKey = Key.newBuilder().getDefaultInstanceForType();
        final var pauseKey = Key.newBuilder().getDefaultInstanceForType();
        final var op = TransactionBody.newBuilder().setTokenCreation(TokenCreateTransactionBody.newBuilder()
                .setTokenType(com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON).setInitialSupply(25)
                .setMaxSupply(21_000_000).setSupplyType(com.hederahashgraph.api.proto.java.TokenSupplyType.FINITE)
                .setDecimals(10).setFreezeDefault(false).setMemo("the mother").setName("bitcoin").setSymbol("BTC")
                .setFeeScheduleKey(feeScheduleKey).setPauseKey(pauseKey).addAllCustomFees(List.of(CustomFee.newBuilder()
                        .setFixedFee(FixedFee.newBuilder().setAmount(10).build())
                        .setFeeCollectorAccountId(asAccount("1.2.3")).build()))
                .setAutoRenewAccount(nonTreasuryAccount.getId().asGrpcAccount())
                .setExpiry(Timestamp.newBuilder().setSeconds(1000L).build()).build()).build();

        subject = Token.fromGrpcOpAndMeta(tokenId, op.getTokenCreation(), treasuryAccount, nonTreasuryAccount, 123);

        assertEquals("bitcoin", subject.getName());
        assertEquals(123L, subject.getExpiry());
        assertEquals(TokenSupplyType.FINITE, subject.getSupplyType());
        assertNotNull(subject.getFeeScheduleKey());
        assertNotNull(subject.getPauseKey());
        assertFalse(subject.isPaused());
    }

    @Test
    void okCreationRelationship() {
        final var frzKey = TxnHandlingScenario.TOKEN_FREEZE_KT.asJKeyUnchecked();
        final var kycKey = TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked();
        subject = subject.setFreezeKey(frzKey);
        subject = subject.setKycKey(kycKey);
    }
    @Test
    void failsInvalidIfLogicImplTriesToChangeNonTreasurySupply() {
        assertFailsWith(() -> subject.burn( 1L), FAIL_INVALID);
        assertFailsWith(() -> subject.mint( 1L, false), FAIL_INVALID);
    }

    @Test
    void cantBurnOrMintNegativeAmounts() {
        assertFailsWith(() -> subject.burn( -1L), INVALID_TOKEN_BURN_AMOUNT);
        assertFailsWith(() -> subject.mint( -1L, false), INVALID_TOKEN_MINT_AMOUNT);
    }

    @Test
    void burnsUniqueAsExpected() {
        subject = subject.setSupplyKey(someKey);
        subject.getLoadedUniqueTokens()
                .putAll(Map.of(10L, new UniqueToken(subject.getId(), 10L, null, treasuryId, null, new byte[] {}), 11L,
                        new UniqueToken(subject.getId(), 11L, null, treasuryId, null, new byte[] {})));
        final var ownershipTracker = mock(OwnershipTracker.class);
        final long serialNumber0 = 10L;
        final long serialNumber1 = 11L;

        subject = subject.burn(ownershipTracker, List.of(serialNumber0, serialNumber1));

        assertEquals(initialSupply - 2, subject.getTotalSupply());
        verify(ownershipTracker).add(subject.getId(), OwnershipTracker.forRemoving(treasuryId, serialNumber0));
        verify(ownershipTracker).add(subject.getId(), OwnershipTracker.forRemoving(treasuryId, serialNumber1));
        assertTrue(subject.hasRemovedUniqueTokens());
        final var removedUniqueTokens = subject.removedUniqueTokens();
        assertEquals(2, removedUniqueTokens.size());
        assertEquals(serialNumber0, removedUniqueTokens.get(0).getSerialNumber());
        assertEquals(serialNumber1, removedUniqueTokens.get(1).getSerialNumber());
    }

    @Test
    void mintsUniqueAsExpected() {
        subject = subject.setSupplyKey(someKey);
        final var ownershipTracker = mock(OwnershipTracker.class);
        subject = subject.mint(ownershipTracker, List.of(ByteString.copyFromUtf8("memo")),
                RichInstant.fromJava(Instant.now()));
        assertEquals(initialSupply + 1, subject.getTotalSupply());
        verify(ownershipTracker).add(eq(subject.getId()), Mockito.any());
        assertTrue(subject.hasMintedUniqueTokens());
        assertEquals(1, subject.mintedUniqueTokens().get(0).getSerialNumber());
        assertEquals(1, subject.getLastUsedSerialNumber());
        assertEquals(TokenType.NON_FUNGIBLE_UNIQUE, subject.getType());
        assertEquals(numPositiveBalances + 1, treasuryAccount.getNumPositiveBalances());
    }

    @Test
    void mintsAsExpected() {
        final long mintAmount = 100L;
        subject = subject.setSupplyKey(someKey);

        // when:

        // then:
        assertEquals(initialSupply + mintAmount, subject.getTotalSupply());
    }

    @Test
    void wipesCommonAsExpected() {
        subject = subject.setSupplyKey(someKey);
        subject = subject.setWipeKey(someKey);

        subject = subject.wipe(10);
        assertEquals(initialSupply - 10, subject.getTotalSupply());
        assertEquals(numPositiveBalances, nonTreasuryAccount.getNumPositiveBalances());


        subject = subject.wipe(30);
        assertEquals(initialSupply - 40, subject.getTotalSupply());
    }

    @Test
    void failsWipingCommonAsExpected() {
        // common setup
        subject = subject.setSupplyKey(someKey);
        // no wipe key
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(non 10));

        // set wipe key
        subject = subject.setWipeKey(someKey);
        // negative amount
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(non -10));

        // wipe treasury
        assertThrows(InvalidTransactionException.class, () -> subject.wipe( 10));

        // negate total supply
        subject = new Token(tokenId, false, TokenType.FUNGIBLE_COMMON, TokenSupplyType.FINITE, 10,
                20000L, null, null, someKey, someKey, null, null, null, false, treasuryAccount, new Account(Id.DEFAULT)
                , false, false, 1000L, true, "the mother", "bitcoin", "BTC", 10, 0L, 1, null);
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(non 11));

        // negate account balance

        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertThrows(InvalidTransactionException.class, () -> subject.wipe(5));
    }

    @Test
    void wipesUniqueAsExpected() {
        subject = subject.setSupplyKey(someKey);
        subject = subject.setWipeKey(someKey);

        final var loadedUniqueTokensMap = (HashMap<Long, UniqueToken>) mock(HashMap.class);
        final var uniqueToken = mock(UniqueToken.class);
        final var owner = nonTreasuryAccount.getId();
        given(uniqueToken.getOwner()).willReturn(owner);
        given(loadedUniqueTokensMap.get(any())).willReturn(uniqueToken);
        subject.getLoadedUniqueTokens().putAll(loadedUniqueTokensMap);

        final var ownershipTracker = mock(OwnershipTracker.class);
        subject = subject.wipe(ownershipTracker, non List.of(1L));
        assertEquals(initialSupply - 1, subject.getTotalSupply());
        verify(ownershipTracker).add(eq(subject.getId()), Mockito.any());
        assertTrue(subject.hasRemovedUniqueTokens());
        assertEquals(1, subject.removedUniqueTokens().get(0).getSerialNumber());
        assertTrue(subject.hasChangedSupply());
        assertEquals(100000, subject.getMaxSupply());
        assertEquals(numPositiveBalances, nonTreasuryAccount.getNumPositiveBalances());


        subject = subject.wipe(ownershipTracker, List.of(1L, 2L));

        assertEquals(initialSupply - 3, subject.getTotalSupply());
        assertTrue(subject.hasRemovedUniqueTokens());
        assertTrue(subject.hasChangedSupply());
        assertEquals(100000, subject.getMaxSupply());
        assertEquals(numPositiveBalances - 1, nonTreasuryAccount.getNumPositiveBalances());
    }

    @Test
    void uniqueWipeFailsAsExpected() {
        subject = subject.setSupplyKey(someKey);

        final Map<Long, UniqueToken> loadedUniqueTokensMap = new HashMap<>();
        subject.getLoadedUniqueTokens().putAll(loadedUniqueTokensMap);

        final var ownershipTracker = mock(OwnershipTracker.class);
        final var singleSerialNumber = List.of(1L);

        /* Invalid to wipe serial numbers for a FUNGIBLE_COMMON token */
        assertFailsWith(() -> subject.wipe(ownershipTracker, non singleSerialNumber), FAIL_INVALID);

        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        /* Must have a wipe key */
        assertFailsWith(() -> subject.wipe(ownershipTracker,  singleSerialNumber), TOKEN_HAS_NO_WIPE_KEY);

        subject = subject.setWipeKey(someKey);
        /* Not allowed to wipe treasury */
        assertFailsWith(() -> subject.wipe(ownershipTracker,  singleSerialNumber),
                CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT);
    }

    @Test
    void uniqueBurnFailsAsExpected() {
        subject = subject.setSupplyKey(someKey);
        final var ownershipTracker = mock(OwnershipTracker.class);
        final List<Long> emptySerialNumber = List.of();
        final var singleSerialNumber = List.of(1L);

        assertThrows(InvalidTransactionException.class, () -> {
            subject.burn(ownershipTracker,  singleSerialNumber);
        });

        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertFailsWith(() -> subject.burn(ownershipTracker,  emptySerialNumber),
                INVALID_TOKEN_BURN_METADATA);
    }

    @Test
    void canOnlyBurnTokensOwnedByTreasury() {
        // setup:
        final var ownershipTracker = mock(OwnershipTracker.class);
        var oneToBurn = new UniqueToken(subject.getId(), 1L, null, nonTreasuryId, null, new byte[] {});

        // given:
        subject = subject.setSupplyKey(someKey);
        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        subject.getLoadedUniqueTokens().putAll(Map.of(1L, oneToBurn));

        // expect:
        assertFailsWith(() -> subject.burn(ownershipTracker,  List.of(1L)), TREASURY_MUST_OWN_BURNED_NFT);

        // and when:

        oneToBurn = new UniqueToken(subject.getId(), 1L, null, treasuryId, null, new byte[] {});
        subject.getLoadedUniqueTokens().putAll(Map.of(1L, oneToBurn));
        assertDoesNotThrow(() -> subject.burn(ownershipTracker,  List.of(1L)));
    }

    @Test
    void cannotMintPastSerialNoLimit() {
        // setup:
        subject = subject.setSupplyKey(someKey);
        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        final var twoMeta = List.of(ByteString.copyFromUtf8("A"), ByteString.copyFromUtf8("Z"));
        subject = subject.setLastUsedSerialNumber(MAX_NUM_ALLOWED - 1);

        assertFailsWith(() -> subject.mint(null,  twoMeta, RichInstant.MISSING_INSTANT),
                SERIAL_NUMBER_LIMIT_REACHED);
    }

    @Test
    void uniqueMintFailsAsExpected() {
        subject = new Token(tokenId, false, TokenType.FUNGIBLE_COMMON, TokenSupplyType.FINITE, 100000,
                20000L, null, null, someKey, null, null, null, null, false, treasuryAccount, new Account(Id.DEFAULT)
                , false, false, 1000L, true, "the mother", "bitcoin", "BTC", 10, 0L, 1, null);
        final var ownershipTracker = mock(OwnershipTracker.class);
        final var metadata = List.of(ByteString.copyFromUtf8("memo"));
        final List<ByteString> emptyMetadata = List.of();

        assertThrows(InvalidTransactionException.class, () -> {
            subject.mint(ownershipTracker,  metadata, RichInstant.fromJava(Instant.now()));
        });

        subject = subject.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        assertThrows(InvalidTransactionException.class, () -> {
            subject.mint(ownershipTracker,  emptyMetadata, RichInstant.fromJava(Instant.now()));
        });
    }

    @Test
    void reflectionObjectHelpersWork() {
        final var otherToken = new Token(new Id(1, 2, 3), false, TokenType.FUNGIBLE_COMMON, TokenSupplyType.FINITE,
                100000,
                20000L, null, null, someKey, null, null, null, null, false, treasuryAccount, new Account(Id.DEFAULT)
                , false, false, 1000L, true, "the mother", "bitcoin", "BTC", 10, 0L, 1, null);

        assertNotEquals(subject, otherToken);
        assertNotEquals(subject.hashCode(), otherToken.hashCode());
    }

    @Test
    void toStringWorks() {
        final var desired = "Token{id=1.2.3, type=null, deleted=false, autoRemoved=false," + " treasury=Account{id=0" +
                ".0.0, expiry=0, balance=0, deleted=false, ownedNfts=0," + " alreadyUsedAutoAssociations=0, " +
                "maxAutoAssociations=0, alias=," + " cryptoAllowances=null, fungibleTokenAllowances=null," + " " +
                "approveForAllNfts=null, numAssociations=2, numPositiveBalances=1," + " ethereumNonce=0}, " +
                "autoRenewAccount=null, kycKey=<N/A>, freezeKey=<N/A>," + " frozenByDefault=false, supplyKey=<N/A>, " +
                "currentSerialNumber=0," + " pauseKey=<N/A>, paused=false}";

        assertEquals(desired, subject.toString());
    }
}
