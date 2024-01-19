/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.store;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.AbstractNft;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.CustomFeeDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.TokenDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.TokenRelationshipDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.UniqueTokenDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.repository.AccountBalanceRepository;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.mirror.web3.repository.TokenBalanceRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityIdUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class StoreImplTest {

    private static final int POSITIVE_BALANCES = 7;
    private static final int NEGATIVE_BALANCES = 8;
    private static final List<TokenAccountAssociationsCount> associationsCount = Arrays.asList(
            new TokenAccountAssociationsCount() {
                @Override
                public Integer getTokenCount() {
                    return POSITIVE_BALANCES;
                }

                @Override
                public boolean getIsPositiveBalance() {
                    return true;
                }
            },
            new TokenAccountAssociationsCount() {
                @Override
                public Integer getTokenCount() {
                    return NEGATIVE_BALANCES;
                }

                @Override
                public boolean getIsPositiveBalance() {
                    return false;
                }
            });
    private static final Address TOKEN_ADDRESS = Address.ALTBN128_ADD;
    private static final Address ACCOUNT_ADDRESS = Address.BLS12_MAP_FP2_TO_G2;
    private static final String ALIAS_HEX = "0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb";
    private static final Address ALIAS = Address.fromHexString(ALIAS_HEX);
    private static final Id TOKEN_ID = Id.fromGrpcAccount(accountIdFromEvmAddress(TOKEN_ADDRESS));
    private static final Id ACCOUNT_ID = Id.fromGrpcAccount(accountIdFromEvmAddress(ACCOUNT_ADDRESS));

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private TokenAccountRepository tokenAccountRepository;

    @Mock
    private TokenBalanceRepository tokenBalanceRepository;

    @Mock
    private CustomFeeDatabaseAccessor customFeeDatabaseAccessor;

    @Mock
    private NftRepository nftRepository;

    @Mock
    private NftAllowanceRepository nftAllowanceRepository;

    @Mock
    private TokenAllowanceRepository tokenAllowanceRepository;

    @Mock
    private CryptoAllowanceRepository cryptoAllowanceRepository;

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private Entity tokenModel;

    @Mock
    private Entity accountModel;

    @Mock
    private Token token;

    @Mock
    private TokenAccount tokenAccount;

    @Mock(strictness = Strictness.LENIENT)
    private Nft nft;

    private StoreImpl subject;

    @BeforeEach
    void setup() {
        final var accountDatabaseAccessor = new AccountDatabaseAccessor(
                entityDatabaseAccessor,
                nftAllowanceRepository,
                nftRepository,
                tokenAllowanceRepository,
                cryptoAllowanceRepository,
                tokenAccountRepository,
                accountBalanceRepository);
        final var tokenDatabaseAccessor = new TokenDatabaseAccessor(
                tokenRepository, entityDatabaseAccessor, entityRepository, customFeeDatabaseAccessor, nftRepository);
        final var tokenRelationshipDatabaseAccessor = new TokenRelationshipDatabaseAccessor(
                tokenDatabaseAccessor,
                accountDatabaseAccessor,
                tokenAccountRepository,
                tokenBalanceRepository,
                nftRepository);
        final var uniqueTokenDatabaseAccessor = new UniqueTokenDatabaseAccessor(nftRepository);
        final var entityDatabaseAccessor = new EntityDatabaseAccessor(entityRepository);
        final List<DatabaseAccessor<Object, ?>> accessors = List.of(
                accountDatabaseAccessor,
                tokenDatabaseAccessor,
                tokenRelationshipDatabaseAccessor,
                uniqueTokenDatabaseAccessor,
                entityDatabaseAccessor);
        final var stackedStateFrames = new StackedStateFrames(accessors);
        subject = new StoreImpl(stackedStateFrames);
    }

    @Test
    void getAccountWithoutThrow() {
        when(entityDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(accountModel));
        when(accountModel.getId()).thenReturn(12L);
        when(accountModel.getNum()).thenReturn(12L);
        when(accountModel.getType()).thenReturn(EntityType.ACCOUNT);
        when(tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(12L))
                .thenReturn(associationsCount);
        final var account = subject.getAccount(ACCOUNT_ADDRESS, OnMissing.DONT_THROW);
        assertThat(account.getId()).isEqualTo(new Id(0, 0, 12));
    }

    @Test
    void getAccountThrowIfMissing() {
        assertThatThrownBy(() -> subject.getAccount(ACCOUNT_ADDRESS, OnMissing.THROW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void updateAccount() {
        final var account = new Account(1L, ACCOUNT_ID, 1L);
        subject.wrap();
        subject.updateAccount(account);
        assertEquals(account, subject.getAccount(ACCOUNT_ADDRESS, OnMissing.DONT_THROW));
    }

    @Test
    void getTokenWithoutThrow() {
        when(entityDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(tokenModel));
        when(tokenModel.getId()).thenReturn(6L);
        when(tokenModel.getNum()).thenReturn(6L);
        when(tokenModel.getType()).thenReturn(EntityType.TOKEN);
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        final var token = subject.getToken(TOKEN_ADDRESS, OnMissing.DONT_THROW);
        assertThat(token.getId()).isEqualTo(new Id(0, 0, 6L));
    }

    @Test
    void getTokenThrowIfMissing() {
        assertThatThrownBy(() -> subject.getToken(TOKEN_ADDRESS, OnMissing.THROW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void updateToken() {
        final var token = new com.hedera.services.store.models.Token(TOKEN_ID);
        subject.wrap();
        subject.updateToken(token);
        assertEquals(token, subject.getToken(TOKEN_ADDRESS, OnMissing.DONT_THROW));
    }

    @Test
    void getTokenRelationshipWithoutThrow() {
        when(entityDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(tokenModel));
        when(tokenModel.getId()).thenReturn(6L);
        when(tokenModel.getNum()).thenReturn(6L);
        when(tokenModel.getType()).thenReturn(EntityType.TOKEN);
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(entityDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(accountModel));
        when(accountModel.getId()).thenReturn(12L);
        when(accountModel.getNum()).thenReturn(12L);
        when(accountModel.getType()).thenReturn(EntityType.ACCOUNT);
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(tokenAccount.getAssociated()).thenReturn(Boolean.TRUE);
        when(token.getType()).thenReturn(TokenTypeEnum.FUNGIBLE_COMMON);
        final var tokenRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);
        assertThat(tokenRelationship.getAccount().getId()).isEqualTo(new Id(0, 0, 12));
        assertThat(tokenRelationship.getToken().getId()).isEqualTo(new Id(0, 0, 6));
    }

    @Test
    void getTokenRelationshipThrowIfMissing() {
        final var tokenRelationshipKey = new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS);
        assertThatThrownBy(() -> subject.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void updateTokenRelationship() {
        var tokenRel = new TokenRelationship(
                new com.hedera.services.store.models.Token(TOKEN_ID), new Account(0L, ACCOUNT_ID, 0L));
        subject.wrap();
        subject.updateTokenRelationship(tokenRel);
        // tokenRel is now persisted in store
        tokenRel = tokenRel.setNotYetPersisted(false);
        assertEquals(
                tokenRel,
                subject.getTokenRelationship(
                        new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW));
    }

    @Test
    void deleteTokenRelationship() {
        setupTokenAndAccount();

        final var tokenRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);

        subject.wrap();
        subject.deleteTokenRelationship(tokenRelationship);

        var postDeleteRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);

        assertThat(postDeleteRelationship.getToken().getId()).isEqualTo(Id.DEFAULT);
        assertThat(postDeleteRelationship.getAccount().getId()).isEqualTo(Id.DEFAULT);
    }

    @Test
    void deleteNotExistingTokenRelationship() {
        final var tokenRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);

        subject.wrap();
        subject.deleteTokenRelationship(tokenRelationship);

        var postDeleteRelationship = subject.getTokenRelationship(
                new TokenRelationshipKey(TOKEN_ADDRESS, ACCOUNT_ADDRESS), OnMissing.DONT_THROW);

        assertThat(postDeleteRelationship.getToken().getId()).isEqualTo(Id.DEFAULT);
        assertThat(postDeleteRelationship.getAccount().getId()).isEqualTo(Id.DEFAULT);
    }

    @Test
    void getUniqueTokenWithoutThrow() {
        final var nftId = new NftId(0, 0, 6, 1);
        when(nftRepository.findActiveById(6, 1)).thenReturn(Optional.of(nft));
        when(nft.getId()).thenReturn(new AbstractNft.Id(1, 6));
        when(nft.getSerialNumber()).thenReturn(1L);
        when(nft.getTokenId()).thenReturn(6L);
        final var uniqueToken = subject.getUniqueToken(nftId, OnMissing.DONT_THROW);
        assertThat(uniqueToken.getNftId()).isEqualTo(nftId);
    }

    @Test
    void getUniqueTokenThrowIfMissing() {
        final var nftId = new NftId(0, 0, 6, 1);
        assertThatThrownBy(() -> subject.getUniqueToken(nftId, OnMissing.THROW))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void updateUniqueToken() {
        final var nftId = new NftId(0, 0, 0, 1);
        final var nft = new UniqueToken(
                Id.DEFAULT,
                1,
                RichInstant.MISSING_INSTANT,
                Id.fromGrpcAccount(accountIdFromEvmAddress(ACCOUNT_ADDRESS)),
                null,
                null);
        subject.wrap();
        subject.updateUniqueToken(nft);
        assertEquals(nft, subject.getUniqueToken(nftId, OnMissing.DONT_THROW));
    }

    @Test
    void loadUniqueTokensWithoutThrow() {
        final var nftId = new NftId(0, 0, 6, 1);
        when(nftRepository.findActiveById(6, 1)).thenReturn(Optional.of(nft));
        when(nft.getId()).thenReturn(new AbstractNft.Id(1, 6));
        when(nft.getSerialNumber()).thenReturn(1L);
        when(nft.getTokenId()).thenReturn(6L);
        final var serials = List.of(nftId.serialNo());
        final var token = new com.hedera.services.store.models.Token(TOKEN_ID);
        final var updatedToken = subject.loadUniqueTokens(token, serials);
        assertThat(updatedToken.getLoadedUniqueTokens()).hasSize(serials.size());
        assertThat(updatedToken.getLoadedUniqueTokens().get(nftId.serialNo()).getNftId())
                .isEqualTo(nftId);
    }

    @Test
    void loadUniqueTokensThrowIfMissing() {
        final var nftId = new NftId(0, 0, 6, 1);
        final var serials = List.of(nftId.serialNo());
        final var token = new com.hedera.services.store.models.Token(TOKEN_ID);
        assertThatThrownBy(() -> subject.loadUniqueTokens(token, serials))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void hasApprovedForAll() {
        when(entityDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(accountModel));
        when(accountModel.getId()).thenReturn(12L);
        when(accountModel.getNum()).thenReturn(12L);
        when(accountModel.getType()).thenReturn(EntityType.ACCOUNT);
        when(tokenAccountRepository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(12L))
                .thenReturn(associationsCount);
        var accountId = EntityIdUtils.accountIdFromEvmAddress(ACCOUNT_ADDRESS);
        var tokenId = EntityIdUtils.tokenIdFromEvmAddress(TOKEN_ADDRESS);
        assertThat(subject.hasApprovedForAll(Address.ZERO, accountId, tokenId)).isFalse();
        assertThat(subject.hasApprovedForAll(ACCOUNT_ADDRESS, accountId, tokenId))
                .isFalse();
    }

    @Test
    void linkAliasAccount() {
        subject.wrap();
        subject.linkAlias(ALIAS, ACCOUNT_ADDRESS);
        assertThat(subject.getAccount(ACCOUNT_ADDRESS, OnMissing.DONT_THROW)).isNotNull();
    }

    @Test
    void getHistoricalTimestamp() {
        subject.wrap();
        assertThat(subject.getHistoricalTimestamp()).isEmpty();
    }

    private void setupTokenAndAccount() {
        when(token.getType()).thenReturn(TokenTypeEnum.FUNGIBLE_COMMON);
        when(entityDatabaseAccessor.get(TOKEN_ADDRESS, Optional.empty())).thenReturn(Optional.of(tokenModel));
        when(tokenModel.getId()).thenReturn(6L);
        when(tokenModel.getNum()).thenReturn(6L);
        when(tokenModel.getType()).thenReturn(EntityType.TOKEN);
        when(tokenRepository.findById(any())).thenReturn(Optional.of(token));
        when(entityDatabaseAccessor.get(ACCOUNT_ADDRESS, Optional.empty())).thenReturn(Optional.of(accountModel));
        when(accountModel.getId()).thenReturn(19L);
        when(accountModel.getNum()).thenReturn(19L);
        when(accountModel.getType()).thenReturn(EntityType.ACCOUNT);
        when(tokenAccountRepository.findById(any())).thenReturn(Optional.of(tokenAccount));
        when(tokenAccount.getAssociated()).thenReturn(Boolean.TRUE);
    }
}
