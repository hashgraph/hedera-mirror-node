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

import static com.hedera.services.store.tokens.HederaTokenStore.asTokenRelationshipKey;
import static com.hedera.services.utils.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.utils.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hedera.services.utils.BitPackUtils.setMaxAutomaticAssociationsTo;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.jproto.JKey;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import java.util.EnumSet;
import java.util.function.UnaryOperator;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaTokenStoreTest {
    private static final Key key = Key.newBuilder()
            .setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .build();
    private static final Key newKey = Key.newBuilder()
            .setEd25519(ByteString.copyFromUtf8("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
            .build();

    private static final int associatedTokensCount = 2;
    private static final int numPositiveBalances = 1;
    public static final long CONSENSUS_NOW = 1_234_567L;
    private static final long expiry = CONSENSUS_NOW + 1_234_567L;
    private static final long newExpiry = CONSENSUS_NOW + 1_432_765L;
    private static final long treasuryBalance = 50_000L;
    private static final TokenID misc = asToken("0.0.1");
    private static final TokenID nonfungible = asToken("0.0.2");
    private static final int maxAutoAssociations = 1234;
    private static final AccountID payer = IdUtils.asAccount("0.0.12345");
    private static final AccountID primaryTreasury = IdUtils.asAccount("0.0.9898");
    private static final AccountID treasury = IdUtils.asAccount("0.0.3");
    private static final AccountID newAutoRenewAccount = IdUtils.asAccount("0.0.6");
    private static final AccountID sponsor = IdUtils.asAccount("0.0.666");
    private static final AccountID counterparty = IdUtils.asAccount("0.0.777");
    private static final AccountID anotherFeeCollector = IdUtils.asAccount("0.0.777");
    private static final TokenRelationshipKey sponsorNft = asTokenRelationshipKey(sponsor, nonfungible);
    private static final TokenRelationshipKey counterpartyNft = asTokenRelationshipKey(counterparty, nonfungible);
    private static final NftId aNft = new NftId(0, 0, 2, 1234);
    private static final NftId tNft = new NftId(0, 0, 2, 12345);
    private static final String newSymbol = "REALLYSOM";
    private static final String newMemo = "NEWMEMO";
    private static final String newName = "NEWNAME";
    private static final AccountID newTreasury = IdUtils.asAccount("0.0.1");
    private static final long newAutoRenewPeriod = 200_000L;
    private static final EnumSet<KeyType> NO_KEYS = EnumSet.noneOf(KeyType.class);
    private static final EnumSet<KeyType> ALL_KEYS = EnumSet.complementOf(EnumSet.of(KeyType.EMPTY_ADMIN));
    private StoreImpl store;
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private HederaTokenStore subject;

    @Mock
    private UnaryOperator<Token> change;

    private static TokenRelationshipKey asTokenRelationshipKey(AccountID accountID, TokenID tokenID) {
        return new TokenRelationshipKey(asTypedEvmAddress(tokenID), asTypedEvmAddress(accountID));
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
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setIsDeleted(true);

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(TOKEN_WAS_DELETED, status);
    }

    @Test
    void associatingRejectsMissingToken() {
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW))
                .willReturn(new Account(0L, Id.fromGrpcAccount(sponsor), 0));

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
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0);
        var token = new Token(Id.fromGrpcToken(misc));

        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, misc), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, account, true));
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.autoAssociate(sponsor, misc);

        assertEquals(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void cannotAutoAssociateIfAccountReachedTokenAssociationLimit() {
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0);
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
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0).setNumAssociations(associatedTokensCount);
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
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var status = subject.adjustBalance(sponsor, misc, 1);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingSender() {
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingReceiver() {
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void changingOwnerRejectsMissingNftInstance() {
        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(sponsor), 0);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0);
        var token = new Token(Id.fromGrpcToken(aNft.tokenId()));

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, sponsorAccount, false));
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(new TokenRelationship(token, counterpartyAccount, false));
        given(store.getUniqueToken(aNft, OnMissing.DONT_THROW)).willReturn(UniqueToken.getEmptyUniqueToken());

        final var status = subject.changeOwner(aNft, sponsor, counterparty);

        assertEquals(INVALID_NFT_ID, status);
    }

    @Test
    void changingOwnerRejectsUnassociatedReceiver() {
        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(sponsor), 0);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0);
        var token = new Token(Id.fromGrpcToken(aNft.tokenId())).setTreasury(sponsorAccount);

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
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

        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0)
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

        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(sponsor), 0)
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

        var counterpartyRel =
                new TokenRelationship(token, counterpartyAccount, true).setBalance(startCounterpartyANfts);
        var sponsorRel = new TokenRelationship(token, sponsorAccount, true).setBalance(startSponsorANfts);

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        counterpartyAccount,
                        updated1CounterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);

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
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0);
        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(sponsor), 0);
        var token = new Token(Id.fromGrpcToken(aNft.tokenId())).setTreasury(sponsorAccount);

        // Set the owner to `counterparty` instead of `sponsor`
        var nft = new UniqueToken(
                Id.fromGrpcToken(aNft.tokenId()),
                1234,
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(counterparty),
                Id.DEFAULT,
                new byte[0]);

        var counterpartyRel = new TokenRelationship(token, counterpartyAccount, false);
        var sponsorRel = new TokenRelationship(token, sponsorAccount, false);

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);

        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(aNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);

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

        var sponsorAccount = new Account(0L, Id.fromGrpcAccount(sponsor), 0)
                .setOwnedNfts(startSponsorNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0)
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
        var counterpartyRel =
                new TokenRelationship(token, counterpartyAccount, false).setBalance(startCounterpartyANfts);
        var sponsorRel = new TokenRelationship(token, sponsorAccount, false).setBalance(startSponsorANfts);

        var updatedNft = nft.setOwner(Id.fromGrpcAccount(counterparty));
        var updatedSponsorAccount = sponsorAccount.setOwnedNfts(startSponsorNfts - 1);
        var updatedCounterpartyAccount = counterpartyAccount.setOwnedNfts(startCounterpartyNfts + 1);
        var updatedSponsorRel = sponsorRel.setBalance(startSponsorANfts - 1);
        var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyANfts + 1);

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(sponsorAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(sponsorAccount);

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

        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(primaryTreasury), 0)
                .setOwnedNfts(startTreasuryNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0)
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
        var counterpartyRel =
                new TokenRelationship(token, counterpartyAccount, false).setBalance(startCounterpartyTNfts);
        var treasuryRel = new TokenRelationship(token, treasuryAccount, false).setBalance(startTreasuryTNfts);

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
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.DONT_THROW))
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

        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(primaryTreasury), 0)
                .setOwnedNfts(startTreasuryNfts)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0)
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
        var counterpartyRel =
                new TokenRelationship(token, counterpartyAccount, false).setBalance(startCounterpartyTNfts);
        var treasuryRel = new TokenRelationship(token, treasuryAccount, false).setBalance(startTreasuryTNfts);

        var updatedNft = nft.setOwner(Id.fromGrpcAccount(counterparty));
        var updatedTreasuryAccount = treasuryAccount.setOwnedNfts(startTreasuryNfts - 1);
        var updatedCounterpartyAccount = counterpartyAccount.setOwnedNfts(startCounterpartyNfts + 1);
        var updatedTreasuryRel = treasuryRel.setBalance(startTreasuryTNfts - 1);
        var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyTNfts + 1);

        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.DONT_THROW))
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
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(primaryTreasury), 0);
        var token = new Token(Id.fromGrpcToken(tNft.tokenId())).setTreasury(treasuryAccount);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount, false);
        var treasuryRel = new TokenRelationship(token, treasuryAccount, false).setFrozen(true);
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
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);

        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getUniqueToken(nft.getNftId(), OnMissing.DONT_THROW)).willReturn(nft);

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
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(primaryTreasury), 0);
        var token = new Token(Id.fromGrpcToken(tNft.tokenId())).setTreasury(treasuryAccount);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount, false).setFrozen(true);
        var treasuryRel = new TokenRelationship(token, treasuryAccount, false);
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
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);

        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.DONT_THROW))
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
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0);

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());

        final var status = subject.adjustBalance(sponsor, misc, 1);

        assertEquals(INVALID_TOKEN_ID, status);
    }

    @Test
    void adjustingRejectsDeletedToken() {
        var account = new Account(0L, Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setIsDeleted(true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(TOKEN_WAS_DELETED, status);
    }

    @Test
    void adjustingRejectsPausedToken() {
        var account = new Account(0L, Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setPaused(true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(TOKEN_IS_PAUSED, status);
    }

    @Test
    void adjustingRejectsFungibleUniqueToken() {
        var account = new Account(0L, Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc)).setType(TokenType.NON_FUNGIBLE_UNIQUE);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);

        final var status = subject.adjustBalance(treasury, misc, 1);

        assertEquals(ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON, status);
    }

    @Test
    void refusesToAdjustFrozenRelationship() {
        var account = new Account(0L, Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account, false).setFrozen(true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.DONT_THROW))
                .willReturn(account);
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
        var account = new Account(0L, Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account, false).setKycGranted(false);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.DONT_THROW))
                .willReturn(account);
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
        var account = new Account(0L, Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account, false);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.DONT_THROW))
                .willReturn(account);
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
        var account = new Account(0L, Id.fromGrpcAccount(anotherFeeCollector), 0)
                .setAutoAssociationMetadata(setMaxAutomaticAssociationsTo(0, 0));
        var token = new Token(Id.fromGrpcToken(misc));

        given(store.getAccount(asTypedEvmAddress(anotherFeeCollector), OnMissing.THROW))
                .willReturn(account);
        given(store.getAccount(asTypedEvmAddress(anotherFeeCollector), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(anotherFeeCollector, misc), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());

        final var status = subject.adjustBalance(anotherFeeCollector, misc, -1);
        assertEquals(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, status);
    }

    @Test
    void adjustmentFailsOnAutomaticAssociationLimitReached() {
        var account = new Account(0L, Id.fromGrpcAccount(anotherFeeCollector), 0).setNumAssociations(1);
        account = account.setAutoAssociationMetadata(
                setMaxAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 3));
        account = account.setAutoAssociationMetadata(
                setAlreadyUsedAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 3));
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account, false)
                .setFrozen(false)
                .setKycGranted(true)
                .setBalance(0);

        given(store.getAccount(asTypedEvmAddress(anotherFeeCollector), OnMissing.THROW))
                .willReturn(account);
        given(store.getAccount(asTypedEvmAddress(anotherFeeCollector), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(anotherFeeCollector, misc), OnMissing.DONT_THROW))
                .willReturn(TokenRelationship.getEmptyTokenRelationship());

        final var status = subject.adjustBalance(anotherFeeCollector, misc, 1);

        assertEquals(NO_REMAINING_AUTOMATIC_ASSOCIATIONS, status);
        verify(store, never()).updateTokenRelationship(any());
        verify(store, never()).updateAccount(any());
    }

    @Test
    void adjustmentWorksAndIncrementsAlreadyUsedAutoAssociationCountForNewAssociation() {
        var account = new Account(0L, Id.fromGrpcAccount(anotherFeeCollector), 0)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        account = account.setAutoAssociationMetadata(
                setMaxAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 5));
        account = account.setAutoAssociationMetadata(
                setAlreadyUsedAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 3));
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account, true)
                .setFrozen(false)
                .setKycGranted(true)
                .setBalance(0);

        var updatedTokenRelationship = tokenRelationship.setBalance(1);
        var updatedAccount1 = account.setNumAssociations(associatedTokensCount + 1)
                .setAutoAssociationMetadata(
                        setAlreadyUsedAutomaticAssociationsTo(account.getAutoAssociationMetadata(), 4));
        var updatedAccount2 = updatedAccount1.setNumPositiveBalances(numPositiveBalances + 1);

        given(store.getAccount(asTypedEvmAddress(anotherFeeCollector), OnMissing.THROW))
                .willReturn(account, account, account, account, account, updatedAccount1);
        given(store.getAccount(asTypedEvmAddress(anotherFeeCollector), OnMissing.DONT_THROW))
                .willReturn(account);
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
        var account = new Account(0L, Id.fromGrpcAccount(treasury), 0)
                .setNumAssociations(associatedTokensCount)
                .setNumPositiveBalances(numPositiveBalances);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account, false).setBalance(1);

        var updatedAccount = account.setNumPositiveBalances(numPositiveBalances - 1);
        var updatedTokenRelationship = tokenRelationship.setBalance(0);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.DONT_THROW))
                .willReturn(account);
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
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0)
                .setNumAssociations(5)
                .setNumPositiveBalances(2);
        var token = new Token(Id.fromGrpcToken(misc)).setDecimals(2);
        var tokenRelationship =
                new TokenRelationship(token, account, false).setFrozen(false).setKycGranted(true);

        final var aa =
                AccountAmount.newBuilder().setAccountID(sponsor).setAmount(100).build();
        final var fungibleChange = BalanceChange.changingFtUnits(Id.fromGrpcToken(misc), misc, aa, payer);
        fungibleChange.setExpectedDecimals(2);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.THROW)).willReturn(account);
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(account);
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

    @Test
    void realAssociationsExist() {
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account, true);

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(sponsor, misc), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);
        assertTrue(subject.associationExists(sponsor, misc));
    }

    @Test
    void noAssociationsWithMissingAccounts() {
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        assertFalse(subject.associationExists(sponsor, misc));
    }

    @Test
    void applicationRejectsMissing() {
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willThrow(InvalidTransactionException.class);

        assertThrows(InvalidTransactionException.class, () -> subject.apply(misc, change));
    }

    @Test
    void applicationAlwaysReplacesModifiableToken() {
        var token = new Token(Id.fromGrpcToken(misc));

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);
        willThrow(IllegalStateException.class).given(change).apply(token);

        assertThrows(IllegalArgumentException.class, () -> subject.apply(misc, change));
    }

    @Test
    void grantingKycRejectsMissingAccount() {
        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var status = subject.grantKyc(sponsor, misc);

        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void grantingKycRejectsDetachedAccount() {
        final var detachedSponsorId =
                AccountID.newBuilder().setAccountNum(666_666).build();
        var detachedSponsor = new Account(0L, Id.fromGrpcAccount(detachedSponsorId), 0);

        given(mirrorNodeEvmProperties.shouldAutoRenewSomeEntityType()).willReturn(true);
        given(mirrorNodeEvmProperties.shouldAutoRenewAccounts()).willReturn(true);
        given(store.getAccount(asTypedEvmAddress(detachedSponsorId), OnMissing.DONT_THROW))
                .willReturn(detachedSponsor);
        given(store.getAccount(asTypedEvmAddress(detachedSponsorId), OnMissing.THROW))
                .willReturn(detachedSponsor);

        final var status = subject.grantKyc(detachedSponsorId, misc);

        assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, status);
    }

    @Test
    void grantingKycRejectsDeletedAccount() {
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0).setDeleted(true);

        given(store.getAccount(asTypedEvmAddress(sponsor), OnMissing.DONT_THROW))
                .willReturn(account);

        final var status = subject.grantKyc(sponsor, misc);

        assertEquals(ACCOUNT_DELETED, status);
    }

    @Test
    void grantingRejectsUnknowableToken() {
        var account = new Account(0L, Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account, true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);

        final var status = subject.grantKyc(treasury, misc);

        assertEquals(TOKEN_HAS_NO_KYC_KEY, status);
    }

    @Test
    void unfreezingInvalidWithoutFreezeKey() {
        var account = new Account(0L, Id.fromGrpcAccount(treasury), 0);
        var token = new Token(Id.fromGrpcToken(misc));
        var tokenRelationship = new TokenRelationship(token, account, true);

        given(store.getAccount(asTypedEvmAddress(treasury), OnMissing.DONT_THROW))
                .willReturn(account);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(treasury, misc), OnMissing.DONT_THROW))
                .willReturn(tokenRelationship);

        final var status = subject.unfreeze(treasury, misc);

        assertEquals(TOKEN_HAS_NO_FREEZE_KEY, status);
    }

    @Test
    void changingOwnerWildCardDoesTheExpectedWithTreasury() {
        final long startTreasuryNfts = 1;
        final long startCounterpartyNfts = 0;
        final long startTreasuryTNfts = 1;
        final long startCounterpartyTNfts = 0;

        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(primaryTreasury), 0).setOwnedNfts(startTreasuryNfts);
        var counterpartyAccount =
                new Account(0L, Id.fromGrpcAccount(counterparty), 0).setOwnedNfts(startCounterpartyNfts);
        var token = new Token(Id.fromGrpcToken(tNft.tokenId()));
        var treasuryRel = new TokenRelationship(token, treasuryAccount, false).setBalance(startTreasuryTNfts);
        var counterpartyRel =
                new TokenRelationship(token, counterpartyAccount, false).setBalance(startCounterpartyTNfts);

        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.THROW))
                .willReturn(counterpartyAccount);
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);

        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);

        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);

        final var status = subject.changeOwnerWildCard(tNft, primaryTreasury, counterparty);

        final var updatedPrimaryTreasury = treasuryAccount.setOwnedNfts(0);
        final var updatedCounterparty = counterpartyAccount.setOwnedNfts(1);
        final var updatedTreasuryRel = treasuryRel.setBalance(startTreasuryTNfts - 1);
        final var updatedCounterpartyRel = counterpartyRel.setBalance(startCounterpartyTNfts + 1);

        assertEquals(OK, status);
        verify(store).updateAccount(updatedPrimaryTreasury);
        verify(store).updateAccount(updatedCounterparty);
        verify(store).updateTokenRelationship(updatedTreasuryRel);
        verify(store).updateTokenRelationship(updatedCounterpartyRel);
    }

    @Test
    void changingOwnerWildCardRejectsFromFreezeAndKYC() {
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(primaryTreasury), 0);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0);
        var token = new Token(Id.fromGrpcToken(tNft.tokenId()));
        var treasuryRel = new TokenRelationship(token, treasuryAccount, false).setFrozen(true);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount, false);

        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);

        final var status = subject.changeOwnerWildCard(tNft, primaryTreasury, counterparty);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void changingOwnerWildCardRejectsToFreezeAndKYC() {
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(primaryTreasury), 0);
        var counterpartyAccount = new Account(0L, Id.fromGrpcAccount(counterparty), 0);
        var token = new Token(Id.fromGrpcToken(tNft.tokenId()));
        var treasuryRel = new TokenRelationship(token, treasuryAccount, false);
        var counterpartyRel = new TokenRelationship(token, counterpartyAccount, false).setFrozen(true);

        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.DONT_THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(primaryTreasury), OnMissing.THROW))
                .willReturn(treasuryAccount);
        given(store.getAccount(asTypedEvmAddress(counterparty), OnMissing.DONT_THROW))
                .willReturn(counterpartyAccount);
        given(store.getToken(asTypedEvmAddress(tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(primaryTreasury, tNft.tokenId()), OnMissing.THROW))
                .willReturn(treasuryRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.DONT_THROW))
                .willReturn(counterpartyRel);
        given(store.getTokenRelationship(asTokenRelationshipKey(counterparty, tNft.tokenId()), OnMissing.THROW))
                .willReturn(counterpartyRel);

        final var status = subject.changeOwnerWildCard(tNft, primaryTreasury, counterparty);

        assertEquals(ACCOUNT_FROZEN_FOR_TOKEN, status);
    }

    @Test
    void updateExpiryInfoRejectsInvalidExpiry() {
        var token = new Token(Id.fromGrpcToken(misc)).setExpiry(expiry);

        final var op = updateWith(NO_KEYS, misc, true, true, false).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(expiry - 1))
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_EXPIRATION_TIME, outcome);
    }

    @Test
    void updateExpiryInfoCanExtendImmutableExpiry() {
        var token = new Token(Id.fromGrpcToken(misc)).setExpiry(expiry);
        final var op = updateWith(NO_KEYS, misc, false, false, false).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234))
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(OK, outcome);
    }

    @Test
    void updateExpiryInfoRejectsInvalidNewAutoRenew() {
        var token = new Token(Id.fromGrpcToken(misc)).setExpiry(expiry);
        final var op = updateWith(NO_KEYS, misc, true, true, false, true, false);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(newAutoRenewAccount), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_AUTORENEW_ACCOUNT, outcome);
    }

    @Test
    void updateExpiryInfoRejectsInvalidNewAutoRenewPeriod() {
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0L);
        var token = new Token(Id.fromGrpcToken(misc)).setExpiry(expiry).setAutoRenewAccount(account);
        final var op = updateWith(NO_KEYS, misc, true, true, false, false, false).toBuilder()
                .setAutoRenewPeriod(enduring(-1L))
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_RENEWAL_PERIOD, outcome);
    }

    @Test
    void updateExpiryInfoRejectsMissingToken() {
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());
        final var op = updateWith(ALL_KEYS, misc, true, true, true);

        final var outcome = subject.updateExpiryInfo(op);

        assertEquals(INVALID_TOKEN_ID, outcome);
    }

    @Test
    void updateRejectsInvalidExpiry() throws DecoderException {
        var token = new Token(Id.fromGrpcToken(misc)).setExpiry(expiry).setAdminKey(JKey.mapKey(key));
        final var op = updateWith(NO_KEYS, misc, true, true, false).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(expiry - 1))
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_EXPIRATION_TIME, outcome);
    }

    @Test
    void canExtendImmutableExpiry() {
        var token = new Token(Id.fromGrpcToken(misc)).setExpiry(expiry).setType(TokenType.FUNGIBLE_COMMON);
        final var op = updateWith(NO_KEYS, misc, false, false, false).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234))
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(OK, outcome);
    }

    @Test
    void cannotUpdateImmutableTokenWithNewFeeScheduleKey() throws DecoderException {
        var token = new Token(Id.fromGrpcToken(misc)).setExpiry(expiry).setFeeScheduleKey(JKey.mapKey(key));

        final var op = updateWith(NO_KEYS, misc, false, false, false).toBuilder()
                .setFeeScheduleKey(key)
                .setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234))
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_IS_IMMUTABLE, outcome);
    }

    @Test
    void cannotUpdateImmutableTokenWithNewPauseKey() throws DecoderException {
        var token = new Token(Id.fromGrpcToken(misc)).setExpiry(expiry).setPauseKey(JKey.mapKey(key));

        final var op = updateWith(NO_KEYS, misc, false, false, false).toBuilder()
                .setPauseKey(key)
                .setExpiry(Timestamp.newBuilder().setSeconds(expiry + 1_234))
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_IS_IMMUTABLE, outcome);
    }

    @Test
    void ifImmutableWillStayImmutable() {
        var token = new Token(Id.fromGrpcToken(misc)).setExpiry(expiry);
        final var op = updateWith(ALL_KEYS, misc, false, false, false).toBuilder()
                .setFeeScheduleKey(key)
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_FEE_SCHEDULE_KEY, outcome);
    }

    @Test
    void cannotUpdateNewPauseKeyIfTokenHasNoPauseKey() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var token = new Token(Id.fromGrpcToken(misc))
                .setExpiry(expiry)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey);
        final var op = updateWith(ALL_KEYS, misc, false, false, false).toBuilder()
                .setPauseKey(key)
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_PAUSE_KEY, outcome);
    }

    @Test
    void updateRejectsInvalidNewAutoRenew() {
        var token = new Token(Id.fromGrpcToken(misc));
        final var op = updateWith(NO_KEYS, misc, true, true, false, true, false);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(newAutoRenewAccount), OnMissing.DONT_THROW))
                .willReturn(Account.getEmptyAccount());

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_AUTORENEW_ACCOUNT, outcome);
    }

    @Test
    void updateRejectsInvalidNewAutoRenewPeriod() throws DecoderException {
        var account = new Account(0L, Id.fromGrpcAccount(sponsor), 0L);
        var jKey = JKey.mapKey(key);
        var token = new Token(Id.fromGrpcToken(misc))
                .setExpiry(expiry)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setAutoRenewAccount(account);
        final var op = updateWith(NO_KEYS, misc, true, true, false, false, false).toBuilder()
                .setAutoRenewPeriod(enduring(-1L))
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_RENEWAL_PERIOD, outcome);
    }

    @Test
    void updateRejectsMissingToken() {
        final var op = updateWith(ALL_KEYS, misc, true, true, true);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(Token.getEmptyToken());

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(INVALID_TOKEN_ID, outcome);
    }

    @Test
    void updateRejectsInappropriateKycKey() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var token = new Token(Id.fromGrpcToken(misc)).setAdminKey(jKey);
        final var op = updateWith(EnumSet.of(KeyType.KYC), misc, false, false, false);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_KYC_KEY, outcome);
    }

    @Test
    void updateRejectsInappropriateFreezeKey() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var token = new Token(Id.fromGrpcToken(misc)).setAdminKey(jKey);

        final var op = updateWith(EnumSet.of(KeyType.FREEZE), misc, false, false, false);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_FREEZE_KEY, outcome);
    }

    @Test
    void updateRejectsInappropriateWipeKey() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var token = new Token(Id.fromGrpcToken(misc)).setAdminKey(jKey);

        final var op = updateWith(EnumSet.of(KeyType.WIPE), misc, false, false, false);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_WIPE_KEY, outcome);
    }

    @Test
    void updateRejectsInappropriateSupplyKey() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var token = new Token(Id.fromGrpcToken(misc)).setAdminKey(jKey);

        final var op = updateWith(EnumSet.of(KeyType.SUPPLY), misc, false, false, false);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, outcome);
    }

    @Test
    void updateRejectsZeroTokenBalanceKey() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var account = new Account(0L, Id.fromGrpcAccount(newTreasury), 0L);
        var token = new Token(Id.fromGrpcToken(nonfungible))
                .setExpiry(expiry)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setType(TokenType.NON_FUNGIBLE_UNIQUE);
        var tokenRelationshipKey = asTokenRelationshipKey(newTreasury, nonfungible);
        var tokenRelationship =
                new TokenRelationship(token, account, true).setKycGranted(true).setBalance(1L);
        final var op = updateWith(ALL_KEYS, nonfungible, true, true, true).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(0))
                .build();

        given(store.getToken(asTypedEvmAddress(nonfungible), OnMissing.DONT_THROW))
                .willReturn(token);
        given(store.getToken(asTypedEvmAddress(nonfungible), OnMissing.THROW)).willReturn(token);
        given(store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW)).willReturn(tokenRelationship);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        assertEquals(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES, outcome);
    }

    @Test
    void updateHappyPathIgnoresZeroExpiry() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var newJkey = JKey.mapKey(newKey);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(treasury), 0L);
        var newTreasuryAccount = new Account(0L, Id.fromGrpcAccount(newTreasury), 0L);
        var token = new Token(Id.fromGrpcToken(misc))
                .setExpiry(expiry)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(ALL_KEYS, misc, true, true, true).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(0))
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(newTreasury), OnMissing.THROW)).willReturn(newTreasuryAccount);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setAdminKey(newJkey)
                .setSymbol(newSymbol)
                .setName(newName)
                .setTreasury(newTreasuryAccount)
                .setFreezeKey(newJkey)
                .setKycKey(newJkey)
                .setSupplyKey(newJkey)
                .setWipeKey(newJkey);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateRemovesAdminKeyWhenAppropos() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(treasury), 0L);
        var token = new Token(Id.fromGrpcToken(misc))
                .setExpiry(expiry)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(EnumSet.of(KeyType.EMPTY_ADMIN), misc, false, false, false);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setAdminKey(null);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateHappyPathWorksForEverythingWithNewExpiry() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var newJkey = JKey.mapKey(newKey);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(treasury), 0L);
        var newTreasuryAccount = new Account(0L, Id.fromGrpcAccount(newTreasury), 0L);
        var token = new Token(Id.fromGrpcToken(misc))
                .setExpiry(expiry)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setFeeScheduleKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(ALL_KEYS, misc, true, true, true).toBuilder()
                .setExpiry(Timestamp.newBuilder().setSeconds(newExpiry))
                .setFeeScheduleKey(newKey)
                .build();

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(newTreasury), OnMissing.THROW)).willReturn(newTreasuryAccount);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setAdminKey(newJkey)
                .setSymbol(newSymbol)
                .setName(newName)
                .setExpiry(newExpiry)
                .setTreasury(newTreasuryAccount)
                .setFreezeKey(newJkey)
                .setKycKey(newJkey)
                .setSupplyKey(newJkey)
                .setWipeKey(newJkey)
                .setFeeScheduleKey(newJkey);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateHappyPathWorksWithNewMemo() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(treasury), 0L);
        var token = new Token(Id.fromGrpcToken(misc))
                .setExpiry(expiry)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setFeeScheduleKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(NO_KEYS, misc, false, false, false, false, false, false, true);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setMemo(newMemo);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateHappyPathWorksWithNewMemoForNonfungible() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(treasury), 0L);
        var token = new Token(Id.fromGrpcToken(misc))
                .setExpiry(expiry)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setFeeScheduleKey(jKey)
                .setType(TokenType.NON_FUNGIBLE_UNIQUE)
                .setTreasury(treasuryAccount);
        final var op = updateWith(NO_KEYS, misc, false, false, false, false, false, false, true);

        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setMemo(newMemo);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    @Test
    void updateHappyPathWorksWithNewAutoRenewAccount() throws DecoderException {
        var jKey = JKey.mapKey(key);
        var newJkey = JKey.mapKey(newKey);
        var treasuryAccount = new Account(0L, Id.fromGrpcAccount(treasury), 0L);
        var newTreasuryAccount = new Account(0L, Id.fromGrpcAccount(newTreasury), 0L);
        var newAccount = new Account(0L, Id.fromGrpcAccount(newAutoRenewAccount), 0L);
        var token = new Token(Id.fromGrpcToken(misc))
                .setExpiry(expiry)
                .setKycKey(jKey)
                .setFreezeKey(jKey)
                .setWipeKey(jKey)
                .setSupplyKey(jKey)
                .setAdminKey(jKey)
                .setFeeScheduleKey(jKey)
                .setType(TokenType.FUNGIBLE_COMMON)
                .setTreasury(treasuryAccount);
        final var op = updateWith(ALL_KEYS, misc, true, true, true, true, true);

        given(mirrorNodeEvmProperties.getMinAutoRenewDuration()).willReturn(1L);
        given(mirrorNodeEvmProperties.getMaxAutoRenewDuration()).willReturn(1_000_000_000L);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.DONT_THROW)).willReturn(token);
        given(store.getToken(asTypedEvmAddress(misc), OnMissing.THROW)).willReturn(token);
        given(store.getAccount(asTypedEvmAddress(newTreasury), OnMissing.THROW)).willReturn(newTreasuryAccount);
        given(store.getAccount(asTypedEvmAddress(newAutoRenewAccount), OnMissing.THROW))
                .willReturn(newAccount);
        given(store.getAccount(asTypedEvmAddress(newAutoRenewAccount), OnMissing.DONT_THROW))
                .willReturn(newAccount);

        final var outcome = subject.update(op, CONSENSUS_NOW);

        final var updatedToken = token.setSymbol(newSymbol)
                .setName(newName)
                .setTreasury(newTreasuryAccount)
                .setAutoRenewAccount(newAccount)
                .setAutoRenewPeriod(newAutoRenewPeriod)
                .setAdminKey(newJkey)
                .setKycKey(newJkey)
                .setFreezeKey(newJkey)
                .setWipeKey(newJkey)
                .setSupplyKey(newJkey);

        assertEquals(OK, outcome);
        verify(store).updateToken(updatedToken);
    }

    private TokenUpdateTransactionBody updateWith(
            final EnumSet<KeyType> keys,
            final TokenID tokenId,
            final boolean useNewSymbol,
            final boolean useNewName,
            final boolean useNewTreasury) {
        return updateWith(keys, tokenId, useNewName, useNewSymbol, useNewTreasury, false, false);
    }

    private TokenUpdateTransactionBody updateWith(
            final EnumSet<KeyType> keys,
            final TokenID tokenId,
            final boolean useNewSymbol,
            final boolean useNewName,
            final boolean useNewTreasury,
            final boolean useNewAutoRenewAccount,
            final boolean useNewAutoRenewPeriod) {
        return updateWith(
                keys,
                tokenId,
                useNewSymbol,
                useNewName,
                useNewTreasury,
                useNewAutoRenewAccount,
                useNewAutoRenewPeriod,
                false,
                false);
    }

    private TokenUpdateTransactionBody updateWith(
            final EnumSet<KeyType> keys,
            final TokenID tokenId,
            final boolean useNewSymbol,
            final boolean useNewName,
            final boolean useNewTreasury,
            final boolean useNewAutoRenewAccount,
            final boolean useNewAutoRenewPeriod,
            final boolean setInvalidKeys,
            final boolean useNewMemo) {
        final var invalidKey = Key.getDefaultInstance();
        final var op = TokenUpdateTransactionBody.newBuilder().setToken(tokenId);
        if (useNewSymbol) {
            op.setSymbol(newSymbol);
        }
        if (useNewName) {
            op.setName(newName);
        }
        if (useNewMemo) {
            op.setMemo(StringValue.newBuilder().setValue(newMemo).build());
        }
        if (useNewTreasury) {
            op.setTreasury(newTreasury);
        }
        if (useNewAutoRenewAccount) {
            op.setAutoRenewAccount(newAutoRenewAccount);
        }
        if (useNewAutoRenewPeriod) {
            op.setAutoRenewPeriod(enduring(newAutoRenewPeriod));
        }
        for (final var key : keys) {
            switch (key) {
                case WIPE -> op.setWipeKey(setInvalidKeys ? invalidKey : newKey);
                case FREEZE -> op.setFreezeKey(setInvalidKeys ? invalidKey : newKey);
                case SUPPLY -> op.setSupplyKey(setInvalidKeys ? invalidKey : newKey);
                case KYC -> op.setKycKey(setInvalidKeys ? invalidKey : newKey);
                case ADMIN -> op.setAdminKey(setInvalidKeys ? invalidKey : newKey);
                case EMPTY_ADMIN -> op.setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY);
            }
        }
        return op.build();
    }

    enum KeyType {
        WIPE,
        FREEZE,
        SUPPLY,
        KYC,
        ADMIN,
        EMPTY_ADMIN
    }

    private Duration enduring(final long secs) {
        return Duration.newBuilder().setSeconds(secs).build();
    }
}
