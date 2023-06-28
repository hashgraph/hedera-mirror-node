/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.store.tokens;

import static com.hedera.services.utils.BitPackUtils.*;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.*;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HederaTokenStoreTest {
    private static final int associatedTokensCount = 2;
    private static final int numPositiveBalances = 1;
    private static final long treasuryBalance = 50_000L;
    private static final TokenID misc = EntityIdUtils.asToken("0.0.1");
    private static final TokenID nonfungible = EntityIdUtils.asToken("0.0.2");
    private static final int maxAutoAssociations = 1234;
    private static final AccountID payer = IdUtils.asAccount("0.0.12345");
    private static final AccountID primaryTreasury = IdUtils.asAccount("0.0.9898");
    private static final AccountID treasury = IdUtils.asAccount("0.0.3");
    private static final AccountID sponsor = IdUtils.asAccount("0.0.666");
    private static final AccountID counterparty = IdUtils.asAccount("0.0.777");
    private static final AccountID anotherFeeCollector = IdUtils.asAccount("0.0.777");
    private static final TokenRelationshipKey sponsorNft = asTokenRelationshipKey(sponsor, nonfungible);
    private static final TokenRelationshipKey counterpartyNft = asTokenRelationshipKey(counterparty, nonfungible);
    private static final NftId aNft = new NftId(0, 0, 2, 1234);
    private static final NftId tNft = new NftId(0, 0, 2, 12345);
    private StoreImpl store;
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private HederaTokenStore subject;

    private static TokenRelationshipKey asTokenRelationshipKey(AccountID accountID, TokenID tokenID) {
        return new TokenRelationshipKey(asTypedEvmAddress(accountID), asTypedEvmAddress(tokenID));
    }

    @BeforeEach
    void setup() {
        mirrorNodeEvmProperties = mock(MirrorNodeEvmProperties.class);
        store = mock(StoreImpl.class);
        var validator = new ContextOptionValidator(mirrorNodeEvmProperties);
        subject = new HederaTokenStore(validator, mirrorNodeEvmProperties, store);
    }

    @Test
    void getThrowsIseOnMissing() {
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());

        assertThrows(IllegalArgumentException.class, () -> subject.get(misc));
    }

    @Test
    void existenceCheckUnderstandsPendingIdOnlyAppliesIfCreationPending() {
        given(store.getToken(asTypedEvmAddress(HederaTokenStore.NO_PENDING_ID), OnMissing.DONT_THROW))
                .willReturn(Token.getEmptyToken());

        assertFalse(subject.exists(HederaTokenStore.NO_PENDING_ID));
    }

    @Test
    void associatingRejectsDeletedTokens() {
        var account = new Account(Id.fromGrpcAccount(sponsor), 0L);
        var token = new Token(Id.fromGrpcToken(misc)).setIsDeleted(true);

        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, misc), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, account));
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(TOKEN_WAS_DELETED, status);
    }

    @Test
    void associatingRejectsMissingToken() {
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW))
                .willReturn(new Account(Id.fromGrpcAccount(sponsor), 0));

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(INVALID_TOKEN_ID, status);
    }

    @Test
    void associatingRejectsMissingAccounts() {
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willThrow(new MissingEntityException(""));

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void associatingRejectsAlreadyAssociatedTokens() {
        var account = new Account(Id.fromGrpcAccount(sponsor), 0L);
        var token = new Token(Id.fromGrpcToken(misc));

        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, misc), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, account));
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void cannotAutoAssociateIfAccountReachedTokenAssociationLimit() {
        var account = new Account(Id.fromGrpcAccount(sponsor), 0L);
        var token = new Token(Id.fromGrpcToken(misc));

        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, misc), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW))
                .willReturn(account.setNumAssociations(associatedTokensCount));
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(mirrorNodeEvmProperties.isLimitTokenAssociations()).willReturn(true);
        given(mirrorNodeEvmProperties.getMaxTokensPerAccount()).willReturn(associatedTokensCount);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
    }

    @Test
    void associatingFailsWhenAutoAssociationLimitReached() {
        var account = new Account(Id.fromGrpcAccount(sponsor), 0L).setNumAssociations(associatedTokensCount);
        account = account.setAutoAssociationMetadata(
                setMaxAutomaticAssociationsTo(account.getAutoAssociationMetadata(), maxAutoAssociations));
        account = account.setAutoAssociationMetadata(
                setAlreadyUsedAutomaticAssociationsTo(account.getAutoAssociationMetadata(), maxAutoAssociations));
        var token = new Token(Id.fromGrpcToken(misc));

        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, misc), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        var status = subject.autoAssociate(sponsor, misc);
        assertEquals(NO_REMAINING_AUTOMATIC_ASSOCIATIONS, status);
    }

    @Test
    void adjustingRejectsMissingAccount() {
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willThrow(new MissingEntityException(""));

        final var status = subject.adjustBalance(sponsor, misc, 1);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingSender() {
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willThrow(new MissingEntityException(""));

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingReceiver() {
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willThrow(new MissingEntityException(""));

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingNftInstance() {
        var sponsorAccount = new Account(Id.fromGrpcAccount(sponsor), 0L);
        var counterpartyAccount = new Account(Id.fromGrpcAccount(counterparty), 0L);
        var token = new Token(Id.fromGrpcToken(aNft.tokenId()));

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, sponsorAccount));
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, counterpartyAccount));
        given(store.getUniqueToken(aNft, OnMissing.DONT_THROW)).willReturn(UniqueToken.getEmptyUniqueToken());

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(INVALID_NFT_ID, status);
    }

    @Test
    void changingOwnerRejectsUnassociatedReceiver() {
        var sponsorAccount = new Account(Id.fromGrpcAccount(sponsor), 0L);
        var counterpartyAccount = new Account(Id.fromGrpcAccount(counterparty), 0L);
        var token = new Token(Id.fromGrpcToken(aNft.tokenId())).setTreasury(sponsorAccount);

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void changingOwnerAutoAssociatesCounterpartyWithOpenSlots() {
        final long startSponsorNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startSponsorANfts = 1;
        final long startCounterpartyANfts = 0;

        var counterpartyAccount = new Account(Id.fromGrpcAccount(counterparty), 0L)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances)
                .setOwnedNfts(startCounterpartyNfts);
        counterpartyAccount = counterpartyAccount.setAutoAssociationMetadata(
                setMaxAutomaticAssociationsTo(counterpartyAccount.getAutoAssociationMetadata(), 100));
        counterpartyAccount = counterpartyAccount.setAutoAssociationMetadata(
                setAlreadyUsedAutomaticAssociationsTo(counterpartyAccount.getAutoAssociationMetadata(), 0));

        final var updated1CounterpartyAccount = counterpartyAccount
                .setNumAssociations(associatedTokensCount + 1)
                .setAutoAssociationMetadata(setAlreadyUsedAutomaticAssociationsTo(
                        counterpartyAccount.getAutoAssociationMetadata(),
                        getAlreadyUsedAutomaticAssociationsFrom(counterpartyAccount.getAutoAssociationMetadata()) + 1));

        final var updated2CounterpartyAccount = updated1CounterpartyAccount
                .setOwnedNfts(updated1CounterpartyAccount.getOwnedNfts() + 1)
                .setNumPositiveBalances(updated1CounterpartyAccount.getNumPositiveBalances() + 1);

        var sponsorAccount = new Account(Id.fromGrpcAccount(sponsor), 0L)
                .setOwnedNfts(startSponsorNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);

        var token = new Token(Id.fromGrpcToken(nonfungible)).setTreasury(sponsorAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(nonfungible),
                1234,
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(sponsor),
                Id.DEFAULT,
                new byte[0]);

        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setBalance(startCounterpartyANfts);
        var sponsorRel = new TokenRelationship(token, sponsorAccount).setBalance(startSponsorANfts);

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        updated1CounterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);

        given(store.getToken(asTypedEvmAddress(nonfungible), OnMissing.THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(nonfungible), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);
        given(store.getUniqueToken(nft.getNftId(), OnMissing.THROW)).willReturn(nft);

        given(store.getTokenRelationship(counterpartyNft, OnMissing.THROW)).willReturn(counterpartyRel);
        given(store.getTokenRelationship(counterpartyNft, OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());
        given(store.getTokenRelationship(sponsorNft, OnMissing.THROW)).willReturn(sponsorRel);
        given(store.getTokenRelationship(sponsorNft, OnMissing.DONT_THROW)).willReturn(sponsorRel);

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(OK, status);
        verify(store).updateAccount(updated1CounterpartyAccount);
        verify(store).updateAccount(updated2CounterpartyAccount);
    }

    @Test
    void changingOwnerRejectsIllegitimateOwner() {
        var counterpartyAccount = new Account(Id.fromGrpcAccount(counterparty), 0L);
        var sponsorAccount = new Account(Id.fromGrpcAccount(sponsor), 0L);
        var token = new Token(Id.fromGrpcToken(aNft.tokenId())).setTreasury(sponsorAccount);

        // Set the owner to `counterparty` instead of `sponsor`
        var nft = new UniqueToken(
                Id.fromGrpcToken(aNft.tokenId()),
                1234,
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(counterparty),
                Id.DEFAULT,
                new byte[0]);

        var counterpartyRel = new TokenRelationship(token, counterpartyAccount);
        var sponsorRel = new TokenRelationship(token, sponsorAccount);

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);

        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);
        given(store.getUniqueToken(nft.getNftId(), OnMissing.THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, aNft.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, aNft.tokenId()), OnMissing.THROW))
                .willReturn(sponsorRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(sponsorRel);

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, status);
    }

    @Test
    void changingOwnerDoesTheExpected() {
        final long startSponsorNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startSponsorANfts = 4;
        final long startCounterpartyANfts = 1;

        var sponsorAccount = new Account(Id.fromGrpcAccount(sponsor), 0L)
                .setOwnedNfts(startSponsorNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var counterpartyAccount = new Account(Id.fromGrpcAccount(counterparty), 0L)
                .setOwnedNfts(startCounterpartyNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var token = new Token(Id.fromGrpcToken(aNft.tokenId())).setTreasury(sponsorAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(aNft.tokenId()),
                aNft.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(sponsor),
                Id.DEFAULT,
                new byte[0]);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setBalance(startCounterpartyANfts);
        var sponsorRel = new TokenRelationship(token, sponsorAccount).setBalance(startSponsorANfts);

        var updatedNft = nft.setOwner(Id.fromGrpcAccount(counterparty));
        var updatedSponsorAccount = sponsorAccount.setOwnedNfts(startSponsorNfts - 1);
        var updatedCounterpartyAccount = counterpartyAccount.setOwnedNfts(startCounterpartyNfts + 1);
        var updatedSponsorRel = sponsorRel.setBalance(startSponsorANfts - 1);
        var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyANfts + 1);

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);

        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);
        given(store.getUniqueToken(nft.getNftId(), OnMissing.THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, aNft.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, aNft.tokenId()), OnMissing.THROW))
                .willReturn(sponsorRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(sponsorRel);

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(OK, status);
        verify(store).updateUniqueToken(updatedNft);
        verify(store).updateAccount(updatedSponsorAccount);
        verify(store).updateAccount(updatedCounterpartyAccount);
        verify(store).updateTokenRelationship(updatedSponsorRel);
        verify(store).updateTokenRelationship(updatedCounterpartyRel);
    }

    @Test
    void changingOwnerDoesTheExpectedWithTreasuryReturn() {
        final long startTreasuryNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startTreasuryTNfts = 4;
        final long startCounterpartyTNfts = 1;

        var treasuryAccount = new Account(Id.fromGrpcAccount(primaryTreasury), 0L)
                .setOwnedNfts(startTreasuryNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var counterpartyAccount = new Account(Id.fromGrpcAccount(counterparty), 0L)
                .setOwnedNfts(startCounterpartyNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var token = new Token(Id.fromGrpcToken(tNft.tokenId())).setTreasury(treasuryAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(tNft.tokenId()),
                tNft.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(counterparty),
                Id.DEFAULT,
                new byte[0]);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setBalance(startCounterpartyTNfts);
        var treasuryRel = new TokenRelationship(token, treasuryAccount).setBalance(startTreasuryTNfts);

        var updatedNft = nft.setOwner(Id.DEFAULT);
        var updatedTreasuryAccount = treasuryAccount.setOwnedNfts(startTreasuryNfts + 1);
        var updatedCounterpartyAccount = counterpartyAccount
                .setOwnedNfts(startCounterpartyNfts - 1)
                .setNumPositiveBalances(numPositiveBalances - 1);
        var updatedTreasuryRel = treasuryRel.setBalance(startTreasuryTNfts + 1);
        var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyTNfts - 1);

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.THROW))
                .willReturn(treasuryAccount);

        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);
        given(store.getUniqueToken(nft.getNftId(), OnMissing.THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwner(tNft, counterparty, primaryTreasury);

        assertEquals(OK, status);
        verify(store).updateUniqueToken(updatedNft);
        verify(store).updateAccount(updatedTreasuryAccount);
        verify(store).updateAccount(updatedCounterpartyAccount);
        verify(store).updateTokenRelationship(updatedTreasuryRel);
        verify(store).updateTokenRelationship(updatedCounterpartyRel);
    }

    @Test
    void changingOwnerDoesTheExpectedWithTreasuryExit() {
        final long startTreasuryNfts = 5;
        final long startCounterpartyNfts = 8;
        final long startTreasuryTNfts = 4;
        final long startCounterpartyTNfts = 1;

        var treasuryAccount = new Account(Id.fromGrpcAccount(primaryTreasury), 0L)
                .setOwnedNfts(startTreasuryNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var counterpartyAccount = new Account(Id.fromGrpcAccount(counterparty), 0L)
                .setOwnedNfts(startCounterpartyNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var token = new Token(Id.fromGrpcToken(tNft.tokenId())).setTreasury(treasuryAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(tNft.tokenId()),
                tNft.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(primaryTreasury),
                Id.DEFAULT,
                new byte[0]);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setBalance(startCounterpartyTNfts);
        var treasuryRel = new TokenRelationship(token, treasuryAccount).setBalance(startTreasuryTNfts);

        var updatedNft = nft.setOwner(Id.fromGrpcAccount(counterparty));
        var updatedTreasuryAccount = treasuryAccount.setOwnedNfts(startTreasuryNfts - 1);
        var updatedCounterpartyAccount = counterpartyAccount.setOwnedNfts(startCounterpartyNfts + 1);
        var updatedTreasuryRel = treasuryRel.setBalance(startTreasuryTNfts - 1);
        var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyTNfts + 1);

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.THROW))
                .willReturn(treasuryAccount);

        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);
        given(store.getUniqueToken(nft.getNftId(), OnMissing.THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwner(tNft, primaryTreasury, counterparty);

        assertEquals(OK, status);

        verify(store).updateUniqueToken(updatedNft);
        verify(store).updateAccount(updatedTreasuryAccount);
        verify(store).updateAccount(updatedCounterpartyAccount);
        verify(store).updateTokenRelationship(updatedTreasuryRel);
        verify(store).updateTokenRelationship(updatedCounterpartyRel);
    }

    @Test
    void changingOwnerRejectsFromFreezeAndKYC() {
        var counterpartyAccount = new Account(Id.fromGrpcAccount(counterparty), 0L);
        var treasuryAccount = new Account(Id.fromGrpcAccount(primaryTreasury), 0L);
        var token = new Token(Id.fromGrpcToken(tNft.tokenId())).setTreasury(treasuryAccount);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount);
        var treasuryRel = new TokenRelationship(token, treasuryAccount).setFrozen(true);
        var nft = new UniqueToken(
                Id.fromGrpcToken(tNft.tokenId()),
                tNft.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(counterparty),
                Id.DEFAULT,
                new byte[0]);

        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwner(tNft, primaryTreasury, counterparty);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void changingOwnerRejectsToFreezeAndKYC() {
        var counterpartyAccount = new Account(Id.fromGrpcAccount(counterparty), 0L);
        var treasuryAccount = new Account(Id.fromGrpcAccount(primaryTreasury), 0L);
        var token = new Token(Id.fromGrpcToken(tNft.tokenId())).setTreasury(treasuryAccount);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount).setFrozen(true);
        var treasuryRel = new TokenRelationship(token, treasuryAccount);
        var nft = new UniqueToken(
                Id.fromGrpcToken(tNft.tokenId()),
                tNft.serialNo(),
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(counterparty),
                Id.DEFAULT,
                new byte[0]);

        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);

        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwner(tNft, primaryTreasury, counterparty);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void adjustingRejectsMissingToken() {
        var account = new Account(Id.fromGrpcAccount(sponsor), 0);

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());

        final var status = subject.adjustBalance(sponsor, misc, 1);

        assertEquals(INVALID_TOKEN_ID, status);
    }

    @Test
    void adjustingRejectsDeletedToken() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setIsDeleted(true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(TOKEN_WAS_DELETED, status);
    }

    @Test
    void adjustingRejectsPausedToken() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setPaused(true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(TOKEN_IS_PAUSED, status);
    }

    @Test
    void adjustingRejectsFungibleUniqueToken() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON, status);
    }

    @Test
    void refusesToAdjustFrozenRelationship() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account).setFrozen(true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(treasury, misc, -1);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void refusesToAdjustRevokedKycRelationship() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account).setKycGranted(false);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(treasury, misc, -1);

        assertEquals(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN, status);
    }

    @Test
    void refusesInvalidAdjustment() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(treasury, misc, -treasuryBalance - 1);

        assertEquals(INSUFFICIENT_TOKEN_BALANCE, status);
    }

    @Test
    void adjustmentFailsOnAutomaticAssociationLimitNotSet() {
        var account = new Account(Id.fromGrpcAccount(anotherFeeCollector), 0)
                .setAutoAssociationMetadata(setMaxAutomaticAssociationsTo(0, 0));
        var token = new Token(Id.fromGrpcToken(misc));

        given(store.getAccount(asTypedEvmAddress(anotherFeeCollector), OnMissing.THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(anotherFeeCollector, misc), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());

        final var status = subject.adjustBalance(anotherFeeCollector, misc, -1);
        assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void adjustmentFailsOnAutomaticAssociationLimitReached() {
        var account = new Account(Id.fromGrpcAccount(anotherFeeCollector), 0).setNumAssociations(1);
        account = account.setAutoAssociationMetadata(
                setMaxAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 3));
        account = account.setAutoAssociationMetadata(
                setAlreadyUsedAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 3));
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account)
                .setFrozen(false)
                .setKycGranted(true)
                .setBalance(0);

        given(store.getAccount(asTypedEvmAddress(anotherFeeCollector), OnMissing.THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(anotherFeeCollector, misc), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());
        given(store.getTokenRelationship(asTokenRelationshipKey(anotherFeeCollector, misc), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(anotherFeeCollector, misc, 1);

        assertEquals(NO_REMAINING_AUTOMATIC_ASSOCIATIONS, status);
        verify(store, never()).updateTokenRelationship(any());
        verify(store, never()).updateAccount(any());
    }

    @Test
    void adjustmentWorksAndIncrementsAlreadyUsedAutoAssociationCountForNewAssociation() {
        var account = new Account(Id.fromGrpcAccount(anotherFeeCollector), 0)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        account = account.setAutoAssociationMetadata(
                setMaxAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 5));
        account = account.setAutoAssociationMetadata(
                setAlreadyUsedAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 3));
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account)
                .setFrozen(false)
                .setKycGranted(true)
                .setBalance(0);

        var updatedTokenRelationship = tokenRelationship.setBalance(1);
        var updatedAccount1 = account.setNumAssociations(associatedTokensCount + 1)
                .setAutoAssociationMetadata(
                        setAlreadyUsedAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 4));
        var updatedAccount2 = updatedAccount1.setNumPositiveBalances(numPositiveBalances + 1);

        given(store.getAccount(asTypedEvmAddress(anotherFeeCollector), OnMissing.THROW))
                .willReturn(account, account, account, account, account, account, updatedAccount1);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(anotherFeeCollector, misc), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());
        given(store.getTokenRelationship(asTokenRelationshipKey(anotherFeeCollector, misc), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(anotherFeeCollector, misc, 1);

        assertEquals(OK, status);
        verify(store).updateTokenRelationship(updatedTokenRelationship);
        verify(store).updateAccount(updatedAccount1);
        verify(store).updateAccount(updatedAccount2);
    }

    @Test
    void performsValidAdjustment() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account).setBalance(1);

        var updatedAccount = account.setNumPositiveBalances(numPositiveBalances - 1);
        var updatedTokenRelationship = tokenRelationship.setBalance(0);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.THROW))
                .willReturn(tokenRelationship);

        subject.adjustBalance(treasury, misc, -1);

        verify(store).updateTokenRelationship(updatedTokenRelationship);
        verify(store).updateAccount(updatedAccount);
    }

    @Test
    void adaptsBehaviorToFungibleType() {
        var account = new Account(Id.fromGrpcAccount(sponsor), 0)
                .setNumAssociations(5)
                .setNumPositiveBalances(2);
        var token = new Token(Id.fromGrpcToken(misc)).setDecimals(2);
        var tokenRelationship =
                new TokenRelationship(token, account).setFrozen(false).setKycGranted(true);

        final var aa =
                AccountAmount.newBuilder().setAccountID(sponsor).setAmount(100).build();
        final var fungibleChange = BalanceChange.changingFtUnits(Id.fromGrpcToken(misc), misc, aa, payer);
        fungibleChange.setExpectedDecimals(2);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, misc), OnMissing.THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, misc), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);

        assertEquals(2, subject.get(misc).getDecimals());
        assertEquals(2, fungibleChange.getExpectedDecimals());

        final var result = subject.tryTokenChange(fungibleChange);
        assertEquals(OK, result);
    }

    @Test
    void failsIfMismatchingDecimals() {
        var token = new Token(Id.fromGrpcToken(misc)).setDecimals(2);
        final var aa =
                AccountAmount.newBuilder().setAccountID(sponsor).setAmount(100).build();
        final var fungibleChange = BalanceChange.changingFtUnits(Id.fromGrpcToken(misc), misc, aa, payer);
        assertFalse(fungibleChange.hasExpectedDecimals());

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        fungibleChange.setExpectedDecimals(4);

        assertEquals(2, subject.get(misc).getDecimals());
        assertEquals(4, fungibleChange.getExpectedDecimals());

        final var result = subject.tryTokenChange(fungibleChange);
        assertEquals(UNEXPECTED_TOKEN_DECIMALS, result);
    }

    @Test
    void decimalMatchingWorks() {
        var token = new Token(Id.fromGrpcToken(misc)).setDecimals(2);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        assertEquals(2, subject.get(misc).getDecimals());
        assertTrue(subject.matchesTokenDecimals(misc, 2));
        assertFalse(subject.matchesTokenDecimals(misc, 4));
    }
}
