/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.*;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HederaTokenStoreTest {
    //    private static final Key newKey = TxnHandlingScenario.TOKEN_REPLACE_KT.asKey();
    private static final String symbol = "NOTHBAR";
    private static final String newSymbol = "REALLYSOM";
    private static final String newMemo = "NEWMEMO";
    private static final String name = "TOKENNAME";
    private static final String newName = "NEWNAME";
    private static final long CONSENSUS_NOW = 1_234_567L;
    private static final int associatedTokensCount = 2;
    private static final int numPositiveBalances = 1;
    private static final long expiry = CONSENSUS_NOW + 1_234_567L;
    private static final long treasuryBalance = 50_000L;
    private static final long sponsorBalance = 1_000L;
    private static final TokenID misc = IdUtils.asToken("0.0.1");
    private static final TokenID nonfungible = IdUtils.asToken("0.0.2");
    private static final int maxAutoAssociations = 1234;
    private static final int alreadyUsedAutoAssocitaions = 123;
    private static final long newAutoRenewPeriod = 2_000_000L;
    private static final AccountID payer = IdUtils.asAccount("0.0.12345");
    private static final AccountID autoRenewAccount = IdUtils.asAccount("0.0.5");
    private static final AccountID newAutoRenewAccount = IdUtils.asAccount("0.0.6");
    private static final AccountID primaryTreasury = IdUtils.asAccount("0.0.9898");
    private static final AccountID treasury = IdUtils.asAccount("0.0.3");
    private static final AccountID newTreasury = IdUtils.asAccount("0.0.1");
    private static final AccountID sponsor = IdUtils.asAccount("0.0.666");
    private static final AccountID counterparty = IdUtils.asAccount("0.0.777");
    private static final AccountID anotherFeeCollector = IdUtils.asAccount("0.0.777");
    private static final TokenID created = IdUtils.asToken("0.0.666666");
    private static final TokenID pending = IdUtils.asToken("0.0.555555");
    private static final TokenRelationshipKey sponsorMisc = asTokenRelationshipKey(sponsor, misc);
    private static final TokenRelationshipKey treasuryNft = asTokenRelationshipKey(primaryTreasury, nonfungible);
    private static final TokenRelationshipKey newTreasuryNft = asTokenRelationshipKey(newTreasury, nonfungible);
    private static final TokenRelationshipKey sponsorNft = asTokenRelationshipKey(sponsor, nonfungible);
    private static final TokenRelationshipKey counterpartyNft = asTokenRelationshipKey(counterparty, nonfungible);
    private static final TokenRelationshipKey treasuryMisc = asTokenRelationshipKey(treasury, misc);
    private static final NftId aNft = new NftId(0, 0, 2, 1234);
    private static final NftId tNft = new NftId(0, 0, 2, 12345);
    private static final TokenRelationshipKey anotherFeeCollectorMisc =
            asTokenRelationshipKey(anotherFeeCollector, misc);
    private StoreImpl store;
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private HederaTokenStore subject;

    private static TokenRelationshipKey asTokenRelationshipKey(AccountID accountID, TokenID tokenID) {
        return new TokenRelationshipKey(asTypedEvmAddress(accountID), asTypedEvmAddress(tokenID));
    }

    @BeforeEach
    void setup() {
        //        token = mock(MerkleToken.class);
        //        given(token.expiry()).willReturn(expiry);
        //        given(token.symbol()).willReturn(symbol);
        //        given(token.hasAutoRenewAccount()).willReturn(true);
        //        given(token.adminKey()).willReturn(Optional.of(TOKEN_ADMIN_KT.asJKeyUnchecked()));
        //        given(token.name()).willReturn(name);
        //        given(token.hasAdminKey()).willReturn(true);
        //        given(token.hasFeeScheduleKey()).willReturn(true);
        //        given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(treasury));
        //        given(token.tokenType()).willReturn(TokenType.FUNGIBLE_COMMON);
        //        given(token.decimals()).willReturn(2);
        //
        //        nonfungibleToken = mock(MerkleToken.class);
        //        given(nonfungibleToken.hasAdminKey()).willReturn(true);
        //        given(nonfungibleToken.tokenType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
        //
        //        ids = mock(EntityIdSource.class);
        //        given(ids.newTokenId(sponsor)).willReturn(created);
        //
        //        hederaLedger = mock(HederaLedger.class);
        //
        //        nftsLedger = (TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter>)
        // mock(TransactionalLedger.class);
        //        given(nftsLedger.get(aNft, OWNER)).willReturn(EntityId.fromGrpcAccountId(sponsor));
        //        given(nftsLedger.get(tNft, OWNER)).willReturn(EntityId.fromGrpcAccountId(primaryTreasury));
        //        given(nftsLedger.exists(aNft)).willReturn(true);
        //        given(nftsLedger.exists(tNft)).willReturn(true);
        //
        //        accountsLedger =
        //                (TransactionalLedger<AccountID, AccountProperty, HederaAccount>)
        // mock(TransactionalLedger.class);
        //        given(accountsLedger.exists(treasury)).willReturn(true);
        //        given(accountsLedger.exists(anotherFeeCollector)).willReturn(true);
        //        given(accountsLedger.exists(autoRenewAccount)).willReturn(true);
        //        given(accountsLedger.exists(newAutoRenewAccount)).willReturn(true);
        //        given(accountsLedger.exists(primaryTreasury)).willReturn(true);
        //        given(accountsLedger.exists(sponsor)).willReturn(true);
        //        given(accountsLedger.exists(counterparty)).willReturn(true);
        //        given(accountsLedger.get(treasury, IS_DELETED)).willReturn(false);
        //        given(accountsLedger.get(autoRenewAccount, IS_DELETED)).willReturn(false);
        //        given(accountsLedger.get(newAutoRenewAccount, IS_DELETED)).willReturn(false);
        //        given(accountsLedger.get(sponsor, IS_DELETED)).willReturn(false);
        //        given(accountsLedger.get(counterparty, IS_DELETED)).willReturn(false);
        //        given(accountsLedger.get(primaryTreasury, IS_DELETED)).willReturn(false);
        //
        //        backingTokens = mock(BackingTokens.class);
        //        given(backingTokens.contains(misc)).willReturn(true);
        //        given(backingTokens.contains(nonfungible)).willReturn(true);
        //        given(backingTokens.getRef(created)).willReturn(token);
        //        given(backingTokens.getImmutableRef(created)).willReturn(token);
        //        given(backingTokens.getRef(misc)).willReturn(token);
        //        given(backingTokens.getImmutableRef(misc)).willReturn(token);
        //        given(backingTokens.getRef(nonfungible)).willReturn(nonfungibleToken);
        //        given(backingTokens.getImmutableRef(tNft.tokenId())).willReturn(nonfungibleToken);
        //        given(backingTokens.getImmutableRef(tNft.tokenId()).treasury())
        //                .willReturn(EntityId.fromGrpcAccountId(primaryTreasury));
        //        given(backingTokens.idSet()).willReturn(Set.of(created));
        //
        //        tokenRelsLedger = mock(TransactionalLedger.class);
        //        given(tokenRelsLedger.exists(sponsorMisc)).willReturn(true);
        //        given(tokenRelsLedger.exists(treasuryNft)).willReturn(true);
        //        given(tokenRelsLedger.exists(sponsorNft)).willReturn(true);
        //        given(tokenRelsLedger.exists(counterpartyNft)).willReturn(true);
        //        given(tokenRelsLedger.get(sponsorMisc, TOKEN_BALANCE)).willReturn(sponsorBalance);
        //        given(tokenRelsLedger.get(sponsorMisc, IS_FROZEN)).willReturn(false);
        //        given(tokenRelsLedger.get(sponsorMisc, IS_KYC_GRANTED)).willReturn(true);
        //        given(tokenRelsLedger.exists(treasuryMisc)).willReturn(true);
        //        given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(true);
        //        given(tokenRelsLedger.get(treasuryMisc, TOKEN_BALANCE)).willReturn(treasuryBalance);
        //        given(tokenRelsLedger.get(treasuryMisc, IS_FROZEN)).willReturn(false);
        //        given(tokenRelsLedger.get(treasuryMisc, IS_KYC_GRANTED)).willReturn(true);
        //        given(tokenRelsLedger.get(treasuryNft, TOKEN_BALANCE)).willReturn(123L);
        //        given(tokenRelsLedger.get(treasuryNft, IS_FROZEN)).willReturn(false);
        //        given(tokenRelsLedger.get(treasuryNft, IS_KYC_GRANTED)).willReturn(true);
        //        given(tokenRelsLedger.get(sponsorNft, TOKEN_BALANCE)).willReturn(123L);
        //        given(tokenRelsLedger.get(sponsorNft, IS_FROZEN)).willReturn(false);
        //        given(tokenRelsLedger.get(sponsorNft, IS_KYC_GRANTED)).willReturn(true);
        //        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE)).willReturn(123L);
        //        given(tokenRelsLedger.get(counterpartyNft, IS_FROZEN)).willReturn(false);
        //        given(tokenRelsLedger.get(counterpartyNft, IS_KYC_GRANTED)).willReturn(true);
        //        given(tokenRelsLedger.get(newTreasuryNft, TOKEN_BALANCE)).willReturn(1L);

        mirrorNodeEvmProperties = mock(MirrorNodeEvmProperties.class);
        store = mock(StoreImpl.class);
        var validator = new ContextOptionValidator(mirrorNodeEvmProperties);
        subject = new HederaTokenStore(validator, mirrorNodeEvmProperties, store);
    }

    //    @Test
    //    void getDelegates() {
    //        assertSame(token, subject.get(misc));
    //    }

    @Test
    void getThrowsIseOnMissing() {
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(Token.getEmptyToken());

        assertThrows(IllegalArgumentException.class, () -> subject.get(misc));
    }

    @Test
    void existenceCheckUnderstandsPendingIdOnlyAppliesIfCreationPending() {
        given(store.getFungibleToken(asTypedEvmAddress(HederaTokenStore.NO_PENDING_ID), OnMissing.DONT_THROW))
                .willReturn(Token.getEmptyToken());

        assertFalse(subject.exists(HederaTokenStore.NO_PENDING_ID));
    }

    //    @Test
    //    void associatingRejectsDeletedTokens() {
    //        given(token.isDeleted()).willReturn(true);
    //
    //        final var status = subject.autoAssociate(sponsor, misc);
    //
    //        assertEquals(TOKEN_WAS_DELETED, status);
    //    }

    @Test
    void associatingRejectsMissingToken() {
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(Token.getEmptyToken());
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
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(token);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void cannotAutoAssociateIfAccountReachedTokenAssociationLimit() {
        var account = new Account(Id.fromGrpcAccount(sponsor), 0L);
        var token = new Token(Id.fromGrpcToken(misc));

        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, misc), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(new Token(Id.DEFAULT), new Account(Id.DEFAULT, 0L)));
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW))
                .willReturn(account.setNumAssociations(associatedTokensCount));
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(token);
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
                .willReturn(new TokenRelationship(new Token(Id.DEFAULT), new Account(Id.DEFAULT, 0L)));
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(token);

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
        given(store.getFungibleToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
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
        given(store.getFungibleToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getFungibleToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.THROW))
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

        given(store.getFungibleToken(asTypedEvmAddress(nonfungible), OnMissing.THROW))
                .willReturn(token);
        given(store.getFungibleToken(asTypedEvmAddress(nonfungible), OnMissing.DONT_THROW))
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

        asTokenRelationshipKey(counterparty, aNft.tokenId());

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);

        given(store.getFungibleToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getFungibleToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
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
    //
    //    @Test
    //    void changingOwnerDoesTheExpected() {
    //        final long startSponsorNfts = 5;
    //        final long startCounterpartyNfts = 8;
    //        final long startSponsorANfts = 4;
    //        final long startCounterpartyANfts = 1;
    //        final var receiver = EntityId.fromGrpcAccountId(counterparty);
    //        final var nftNumPair1 = NftNumPair.fromLongs(1111, 111);
    //        final var nftId1 = nftNumPair1.nftId();
    //        final var nftNumPair2 = NftNumPair.fromLongs(1112, 112);
    //        final var nftId2 = nftNumPair2.nftId();
    //        final var nftNumPair3 = NftNumPair.fromLongs(1113, 113);
    //        final var nftId3 = nftNumPair3.nftId();
    //        given(accountsLedger.get(sponsor, NUM_NFTS_OWNED)).willReturn(startSponsorNfts);
    //        given(accountsLedger.get(counterparty, NUM_NFTS_OWNED)).willReturn(startCounterpartyNfts);
    //        given(tokenRelsLedger.get(sponsorNft, TOKEN_BALANCE)).willReturn(startSponsorANfts);
    //        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE)).willReturn(startCounterpartyANfts);
    //        given(accountsLedger.get(sponsor, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
    //        given(accountsLedger.get(sponsor, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
    //        given(accountsLedger.get(counterparty, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
    //        given(accountsLedger.get(counterparty, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
    //
    //        final var status = subject.changeOwner(aNft, sponsor, counterparty);
    //
    //        assertEquals(OK, status);
    //        verify(accountsLedger, never()).set(counterparty, NUM_ASSOCIATIONS, associatedTokensCount + 1);
    //        verify(accountsLedger, never()).set(counterparty, NUM_POSITIVE_BALANCES, numPositiveBalances + 1);
    //        verify(nftsLedger).set(aNft, OWNER, receiver);
    //        verify(accountsLedger).set(sponsor, NUM_NFTS_OWNED, startSponsorNfts - 1);
    //        verify(accountsLedger).set(counterparty, NUM_NFTS_OWNED, startCounterpartyNfts + 1);
    //        verify(tokenRelsLedger).set(sponsorNft, TOKEN_BALANCE, startSponsorANfts - 1);
    //        verify(tokenRelsLedger).set(counterpartyNft, TOKEN_BALANCE, startCounterpartyANfts + 1);
    //        assertSoleTokenChangesAreForNftTransfer(aNft, sponsor, counterparty);
    //    }
    //
    //    @Test
    //    void changingOwnerDoesTheExpectedWithTreasuryReturn() {
    //        final long startTreasuryNfts = 5;
    //        final long startCounterpartyNfts = 8;
    //        final long startTreasuryTNfts = 4;
    //        final long startCounterpartyTNfts = 1;
    //        final var sender = EntityId.fromGrpcAccountId(counterparty);
    //        final var receiver = EntityId.fromGrpcAccountId(primaryTreasury);
    //        final var muti = EntityNumPair.fromLongs(tNft.tokenId().getTokenNum(), tNft.serialNo());
    //        given(backingTokens.getImmutableRef(tNft.tokenId()).treasury()).willReturn(receiver);
    //        given(accountsLedger.get(primaryTreasury, NUM_NFTS_OWNED)).willReturn(startTreasuryNfts);
    //        given(accountsLedger.get(counterparty, NUM_NFTS_OWNED)).willReturn(startCounterpartyNfts);
    //        given(tokenRelsLedger.get(treasuryNft, TOKEN_BALANCE)).willReturn(startTreasuryTNfts);
    //        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE)).willReturn(startCounterpartyTNfts);
    //        given(nftsLedger.get(tNft, OWNER)).willReturn(EntityId.fromGrpcAccountId(counterparty));
    //        given(accountsLedger.get(primaryTreasury, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
    //        given(accountsLedger.get(primaryTreasury, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
    //        given(accountsLedger.get(counterparty, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
    //        given(accountsLedger.get(counterparty, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
    //
    //        final var status = subject.changeOwner(tNft, counterparty, primaryTreasury);
    //
    //        assertEquals(OK, status);
    //        verify(nftsLedger).set(tNft, OWNER, MISSING_ENTITY_ID);
    //        verify(accountsLedger).set(primaryTreasury, NUM_NFTS_OWNED, startTreasuryNfts + 1);
    //        verify(accountsLedger).set(counterparty, NUM_NFTS_OWNED, startCounterpartyNfts - 1);
    //        verify(tokenRelsLedger).set(treasuryNft, TOKEN_BALANCE, startTreasuryTNfts + 1);
    //        verify(tokenRelsLedger).set(counterpartyNft, TOKEN_BALANCE, startCounterpartyTNfts - 1);
    //        verify(accountsLedger).set(primaryTreasury, NUM_POSITIVE_BALANCES, numPositiveBalances);
    //        verify(accountsLedger).set(counterparty, NUM_POSITIVE_BALANCES, numPositiveBalances - 1);
    //        assertSoleTokenChangesAreForNftTransfer(tNft, counterparty, primaryTreasury);
    //    }
    //
    //    @Test
    //    void changingOwnerDoesTheExpectedWithTreasuryExit() {
    //        final long startTreasuryNfts = 5;
    //        final long startCounterpartyNfts = 8;
    //        final long startTreasuryTNfts = 4;
    //        final long startCounterpartyTNfts = 1;
    //        final var sender = EntityId.fromGrpcAccountId(primaryTreasury);
    //        final var receiver = EntityId.fromGrpcAccountId(counterparty);
    //        final var nftNumPair3 = NftNumPair.fromLongs(1113, 113);
    //        final var nftId3 = nftNumPair3.nftId();
    //        given(accountsLedger.get(primaryTreasury, NUM_NFTS_OWNED)).willReturn(startTreasuryNfts);
    //        given(accountsLedger.get(counterparty, NUM_NFTS_OWNED)).willReturn(startCounterpartyNfts);
    //        given(tokenRelsLedger.get(treasuryNft, TOKEN_BALANCE)).willReturn(startTreasuryTNfts);
    //        given(tokenRelsLedger.get(counterpartyNft, TOKEN_BALANCE)).willReturn(startCounterpartyTNfts);
    //        given(nftsLedger.get(tNft, OWNER)).willReturn(EntityId.MISSING_ENTITY_ID);
    //        given(backingTokens.getImmutableRef(tNft.tokenId()).treasury()).willReturn(sender);
    //        given(accountsLedger.get(primaryTreasury, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
    //        given(accountsLedger.get(primaryTreasury, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
    //        given(accountsLedger.get(counterparty, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
    //        given(accountsLedger.get(counterparty, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
    //
    //        final var status = subject.changeOwner(tNft, primaryTreasury, counterparty);
    //
    //        assertEquals(OK, status);
    //        verify(nftsLedger).set(tNft, OWNER, receiver);
    //        verify(accountsLedger).set(primaryTreasury, NUM_NFTS_OWNED, startTreasuryNfts - 1);
    //        verify(accountsLedger).set(counterparty, NUM_NFTS_OWNED, startCounterpartyNfts + 1);
    //        verify(accountsLedger).set(counterparty, NUM_NFTS_OWNED, startCounterpartyNfts + 1);
    //        verify(tokenRelsLedger).set(treasuryNft, TOKEN_BALANCE, startTreasuryTNfts - 1);
    //        verify(tokenRelsLedger).set(counterpartyNft, TOKEN_BALANCE, startCounterpartyTNfts + 1);
    //        verify(accountsLedger).set(primaryTreasury, NUM_POSITIVE_BALANCES, numPositiveBalances);
    //        verify(accountsLedger).set(counterparty, NUM_POSITIVE_BALANCES, numPositiveBalances);
    //        assertSoleTokenChangesAreForNftTransfer(tNft, primaryTreasury, counterparty);
    //    }
    //
    //    @Test
    //    void changingOwnerRejectsFromFreezeAndKYC() {
    //        given(tokenRelsLedger.get(treasuryNft, IS_FROZEN)).willReturn(true);
    //
    //        final var status = subject.changeOwner(tNft, primaryTreasury, counterparty);
    //
    //        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    //    }
    //
    //    @Test
    //    void changingOwnerRejectsToFreezeAndKYC() {
    //        given(tokenRelsLedger.get(counterpartyNft, IS_FROZEN)).willReturn(true);
    //
    //        final var status = subject.changeOwner(tNft, primaryTreasury, counterparty);
    //
    //        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    //    }
    //
    //    private TokenUpdateTransactionBody updateWith(
    //            final EnumSet<KeyType> keys,
    //            final TokenID tokenId,
    //            final boolean useNewSymbol,
    //            final boolean useNewName,
    //            final boolean useNewTreasury) {
    //        return updateWith(keys, tokenId, useNewName, useNewSymbol, useNewTreasury, false, false);
    //    }
    //
    //    private TokenUpdateTransactionBody updateWith(
    //            final EnumSet<KeyType> keys,
    //            final TokenID tokenId,
    //            final boolean useNewSymbol,
    //            final boolean useNewName,
    //            final boolean useNewTreasury,
    //            final boolean useNewAutoRenewAccount,
    //            final boolean useNewAutoRenewPeriod) {
    //        return updateWith(
    //                keys,
    //                tokenId,
    //                useNewSymbol,
    //                useNewName,
    //                useNewTreasury,
    //                useNewAutoRenewAccount,
    //                useNewAutoRenewPeriod,
    //                false,
    //                false);
    //    }
    //
    //    private TokenUpdateTransactionBody updateWith(
    //            final EnumSet<KeyType> keys,
    //            final TokenID tokenId,
    //            final boolean useNewSymbol,
    //            final boolean useNewName,
    //            final boolean useNewTreasury,
    //            final boolean useNewAutoRenewAccount,
    //            final boolean useNewAutoRenewPeriod,
    //            final boolean setInvalidKeys,
    //            final boolean useNewMemo) {
    //        final var invalidKey = Key.getDefaultInstance();
    //        final var op = TokenUpdateTransactionBody.newBuilder().setToken(tokenId);
    //        if (useNewSymbol) {
    //            op.setSymbol(newSymbol);
    //        }
    //        if (useNewName) {
    //            op.setName(newName);
    //        }
    //        if (useNewMemo) {
    //            op.setMemo(StringValue.newBuilder().setValue(newMemo).build());
    //        }
    //        if (useNewTreasury) {
    //            op.setTreasury(newTreasury);
    //        }
    //        if (useNewAutoRenewAccount) {
    //            op.setAutoRenewAccount(newAutoRenewAccount);
    //        }
    //        if (useNewAutoRenewPeriod) {
    //            op.setAutoRenewPeriod(enduring(newAutoRenewPeriod));
    //        }
    //        for (final var key : keys) {
    //            switch (key) {
    //                case WIPE:
    //                    op.setWipeKey(setInvalidKeys ? invalidKey : newKey);
    //                    break;
    //                case FREEZE:
    //                    op.setFreezeKey(setInvalidKeys ? invalidKey : newKey);
    //                    break;
    //                case SUPPLY:
    //                    op.setSupplyKey(setInvalidKeys ? invalidKey : newKey);
    //                    break;
    //                case KYC:
    //                    op.setKycKey(setInvalidKeys ? invalidKey : newKey);
    //                    break;
    //                case ADMIN:
    //                    op.setAdminKey(setInvalidKeys ? invalidKey : newKey);
    //                    break;
    //                case EMPTY_ADMIN:
    //                    op.setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY);
    //                    break;
    //            }
    //        }
    //        return op.build();
    //    }
    //
    //    private void givenUpdateTarget(final EnumSet<KeyType> keys, final MerkleToken token) {
    //        if (keys.contains(KeyType.WIPE)) {
    //            given(token.hasWipeKey()).willReturn(true);
    //        }
    //        if (keys.contains(KeyType.FREEZE)) {
    //            given(token.hasFreezeKey()).willReturn(true);
    //        }
    //        if (keys.contains(KeyType.SUPPLY)) {
    //            given(token.hasSupplyKey()).willReturn(true);
    //        }
    //        if (keys.contains(KeyType.KYC)) {
    //            given(token.hasKycKey()).willReturn(true);
    //        }
    //        if (keys.contains(KeyType.FEE_SCHEDULE)) {
    //            given(token.hasFeeScheduleKey()).willReturn(true);
    //        }
    //        if (keys.contains(KeyType.PAUSE)) {
    //            given(token.hasPauseKey()).willReturn(true);
    //        }
    //    }

    @Test
    void adjustingRejectsMissingToken() {
        var account = new Account(Id.fromGrpcAccount(sponsor), 0);

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(Token.getEmptyToken());

        final var status = subject.adjustBalance(sponsor, misc, 1);

        assertEquals(INVALID_TOKEN_ID, status);
    }

    @Test
    void adjustingRejectsDeletedToken() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setIsDeleted(true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(token);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(TOKEN_WAS_DELETED, status);
    }

    @Test
    void adjustingRejectsPausedToken() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setPaused(true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(token);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(TOKEN_IS_PAUSED, status);
    }

    @Test
    void adjustingRejectsFungibleUniqueToken() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(token);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON, status);
    }

    @Test
    void refusesToAdjustFrozenRelationship() {
        var account = new Account(Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account).setFrozen(true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(token);
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
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(token);
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
        given(store.getFungibleToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.THROW))
                .willReturn(tokenRelationship);

        final var status = subject.adjustBalance(treasury, misc, -treasuryBalance - 1);

        assertEquals(INSUFFICIENT_TOKEN_BALANCE, status);
    }
    //
    //    @Test
    //    void adjustmentFailsOnAutomaticAssociationLimitNotSet() {
    //        given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(false);
    //        given(accountsLedger.get(anotherFeeCollector, MAX_AUTOMATIC_ASSOCIATIONS))
    //                .willReturn(0);
    //
    //        final var status = subject.adjustBalance(anotherFeeCollector, misc, -1);
    //        assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
    //    }
    //
    //    @Test
    //    void adjustmentFailsOnAutomaticAssociationLimitReached() {
    //        given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(false);
    //        given(tokenRelsLedger.get(anotherFeeCollectorMisc, IS_FROZEN)).willReturn(false);
    //        given(tokenRelsLedger.get(anotherFeeCollectorMisc, IS_KYC_GRANTED)).willReturn(true);
    //        given(tokenRelsLedger.get(anotherFeeCollectorMisc, TOKEN_BALANCE)).willReturn(0L);
    //        given(accountsLedger.get(anotherFeeCollector, MAX_AUTOMATIC_ASSOCIATIONS))
    //                .willReturn(3);
    //        given(accountsLedger.get(anotherFeeCollector, NUM_ASSOCIATIONS)).willReturn(1);
    //        given(accountsLedger.get(anotherFeeCollector, USED_AUTOMATIC_ASSOCIATIONS))
    //                .willReturn(3);
    //        given(usageLimits.areCreatableTokenRels(1)).willReturn(true);
    //
    //        final var status = subject.adjustBalance(anotherFeeCollector, misc, 1);
    //
    //        assertEquals(NO_REMAINING_AUTOMATIC_ASSOCIATIONS, status);
    //        verify(tokenRelsLedger, never()).set(anotherFeeCollectorMisc, TOKEN_BALANCE, 1L);
    //        verify(accountsLedger, never()).set(anotherFeeCollector, USED_AUTOMATIC_ASSOCIATIONS, 4);
    //    }
    //
    //    @Test
    //    void adjustmentWorksAndIncrementsAlreadyUsedAutoAssociationCountForNewAssociation() {
    //        given(tokenRelsLedger.exists(anotherFeeCollectorMisc)).willReturn(false);
    //        given(tokenRelsLedger.get(anotherFeeCollectorMisc, IS_FROZEN)).willReturn(false);
    //        given(tokenRelsLedger.get(anotherFeeCollectorMisc, IS_KYC_GRANTED)).willReturn(true);
    //        given(tokenRelsLedger.get(anotherFeeCollectorMisc, TOKEN_BALANCE)).willReturn(0L);
    //        given(accountsLedger.get(anotherFeeCollector, MAX_AUTOMATIC_ASSOCIATIONS))
    //                .willReturn(5);
    //        given(accountsLedger.get(anotherFeeCollector, USED_AUTOMATIC_ASSOCIATIONS))
    //                .willReturn(3);
    //        given(accountsLedger.get(anotherFeeCollector, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
    //        given(accountsLedger.get(anotherFeeCollector, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
    //        given(usageLimits.areCreatableTokenRels(1)).willReturn(true);
    //
    //        final var status = subject.adjustBalance(anotherFeeCollector, misc, 1);
    //
    //        assertEquals(OK, status);
    //        verify(tokenRelsLedger).set(anotherFeeCollectorMisc, TOKEN_BALANCE, 1L);
    //        verify(accountsLedger).set(anotherFeeCollector, USED_AUTOMATIC_ASSOCIATIONS, 4);
    //        verify(accountsLedger).set(anotherFeeCollector, NUM_ASSOCIATIONS, associatedTokensCount + 1);
    //        verify(accountsLedger).set(anotherFeeCollector, NUM_POSITIVE_BALANCES, numPositiveBalances + 1);
    //    }
    //
    //    @Test
    //    void performsValidAdjustment() {
    //        given(tokenRelsLedger.get(treasuryMisc, TOKEN_BALANCE)).willReturn(1L);
    //        given(accountsLedger.get(treasury, NUM_ASSOCIATIONS)).willReturn(associatedTokensCount);
    //        given(accountsLedger.get(treasury, NUM_POSITIVE_BALANCES)).willReturn(numPositiveBalances);
    //
    //        subject.adjustBalance(treasury, misc, -1);
    //
    //        verify(tokenRelsLedger).set(treasuryMisc, TOKEN_BALANCE, 0L);
    //        verify(accountsLedger).set(treasury, NUM_POSITIVE_BALANCES, numPositiveBalances - 1);
    //    }
    //
    //    @Test
    //    void adaptsBehaviorToFungibleType() {
    //        final var aa =
    //                AccountAmount.newBuilder().setAccountID(sponsor).setAmount(100).build();
    //        final var fungibleChange = BalanceChange.changingFtUnits(Id.fromGrpcToken(misc), misc, aa, payer);
    //        fungibleChange.setExpectedDecimals(2);
    //        given(accountsLedger.get(sponsor, NUM_ASSOCIATIONS)).willReturn(5);
    //        given(accountsLedger.get(sponsor, NUM_POSITIVE_BALANCES)).willReturn(2);
    //
    //        assertEquals(2, subject.get(misc).decimals());
    //        assertEquals(2, fungibleChange.getExpectedDecimals());
    //
    //        final var result = subject.tryTokenChange(fungibleChange);
    //        Assertions.assertEquals(OK, result);
    //    }
    //
    //    @Test
    //    void failsIfMismatchingDecimals() {
    //        final var aa =
    //                AccountAmount.newBuilder().setAccountID(sponsor).setAmount(100).build();
    //        final var fungibleChange = BalanceChange.changingFtUnits(Id.fromGrpcToken(misc), misc, aa, payer);
    //        assertFalse(fungibleChange.hasExpectedDecimals());
    //
    //        fungibleChange.setExpectedDecimals(4);
    //
    //        assertEquals(2, subject.get(misc).decimals());
    //        assertEquals(4, fungibleChange.getExpectedDecimals());
    //
    //        final var result = subject.tryTokenChange(fungibleChange);
    //        Assertions.assertEquals(UNEXPECTED_TOKEN_DECIMALS, result);
    //    }
    //
    //    @Test
    //    void decimalMatchingWorks() {
    //        assertEquals(2, subject.get(misc).decimals());
    //        assertTrue(subject.matchesTokenDecimals(misc, 2));
    //        assertFalse(subject.matchesTokenDecimals(misc, 4));
    //    }
    //
    //    private void assertSoleTokenChangesAreForNftTransfer(final NftId nft, final AccountID from, final AccountID
    // to) {
    //        final var tokenChanges = sideEffectsTracker.getNetTrackedTokenUnitAndOwnershipChanges();
    //        final var ownershipChange = tokenChanges.get(0);
    //        assertEquals(nft.tokenId(), ownershipChange.getToken());
    //        final var nftTransfer = ownershipChange.getNftTransfers(0);
    //        assertEquals(nft.serialNo(), nftTransfer.getSerialNumber());
    //        assertEquals(from, nftTransfer.getSenderAccountID());
    //        assertEquals(to, nftTransfer.getReceiverAccountID());
    //    }

    private Duration enduring(final long secs) {
        return Duration.newBuilder().setSeconds(secs).build();
    }

    enum KeyType {
        WIPE,
        FREEZE,
        SUPPLY,
        KYC,
        ADMIN,
        EMPTY_ADMIN,
        FEE_SCHEDULE,
        PAUSE
    }
}
