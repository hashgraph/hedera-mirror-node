package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOKEN;
import static com.hedera.mirror.importer.domain.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.importer.domain.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import javax.annotation.Resource;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftTransfer;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenSupplyTypeEnum;
import com.hedera.mirror.importer.domain.TokenTransfer;
import com.hedera.mirror.importer.domain.TokenTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.NftTransferRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.TokenTransferRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.44.1")
class SupportDeletedTokenDissociateMigrationTest extends IntegrationTest {

    private static final int TRANSACTION_TYPE_TOKEN_DISSOCIATE = 41;
    private static final EntityId TREASURY = EntityId.of("0.0.200", ACCOUNT);
    private static final EntityId NEW_TREASURY = EntityId.of("0.0.201", ACCOUNT);

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.45.0__support_deleted_token_dissociate.sql")
    private File migrationSql;

    @Resource
    private NftRepository nftRepository;

    @Resource
    private NftTransferRepository nftTransferRepository;

    @Resource
    private TokenAccountRepository tokenAccountRepository;

    @Resource
    private TokenRepository tokenRepository;

    @Resource
    private TokenTransferRepository tokenTransferRepository;

    @Resource
    private TransactionRepository transactionRepository;

    @Test
    void verify() {
        // given
        // entities
        // - 2 ft classes
        //   - deleted, account1's token dissociate includes token transfer
        //   - still alive
        // - 3 nft classes
        //   - deleted, account1's token dissociate doesn't include token transfer, account2's includes
        //   - deleted, account1's token dissociate doesn't include token transfer, account2's dissociate happened
        //     before token deletion
        //   - still alive
        EntityId account1 = EntityId.of("0.0.210", ACCOUNT);
        EntityId account2 = EntityId.of("0.0.211", ACCOUNT);
        EntityId ftId1 = EntityId.of("0.0.500", TOKEN);
        EntityId ftId2 = EntityId.of("0.0.501", TOKEN);
        EntityId nftId1 = EntityId.of("0.0.502", TOKEN);
        EntityId nftId2 = EntityId.of("0.0.503", TOKEN);
        EntityId nftId3 = EntityId.of("0.0.504", TOKEN);

        Token ftClass1 = token( 10L, ftId1, FUNGIBLE_COMMON);
        Token ftClass2 = token(15L, ftId2, FUNGIBLE_COMMON);
        Token nftClass1 = token(20L, nftId1, NON_FUNGIBLE_UNIQUE);
        Token nftClass2 = token(25L, nftId2, NON_FUNGIBLE_UNIQUE);
        Token nftClass3 = token(30L, nftId3, NON_FUNGIBLE_UNIQUE);

        Entity ft1Entity = entity(ftClass1, true, 50L);
        Entity ft2Entity = entity(ftClass2);
        Entity nft1Entity = entity(nftClass1, true, 55L);
        Entity nft2Entity = entity(nftClass2, true, 60L);
        Entity nft3Entity = entity(nftClass3);

        entityRepository.saveAll(List.of(ft1Entity, ft2Entity, nft1Entity, nft2Entity, nft3Entity));
        tokenRepository.saveAll(List.of(ftClass1, ftClass2, nftClass1, nftClass2, nftClass3));

        long account1Ft1DissociateTimestamp = 70;
        long account1Nft1DissociateTimestamp = 75;
        long account2Nft1DissociateTimestamp = 80;
        long account1Nft2DissociateTimestamp = 85;
        long account2Nft2DissociateTimestamp = 55; // happened before token deletion
        List<TokenAccount> tokenAccounts = List.of(
                tokenAccount(account1, true, 12L, 12L, ftId1),
                tokenAccount(account1, false, 12L, account1Ft1DissociateTimestamp, ftId1),
                tokenAccount(account2, true, 15L, 15L, ftId1),
                tokenAccount(account1, true, 20L, 20L, ftId2),
                tokenAccount(account1, true, 23L, 23L, nftId1),
                tokenAccount(account1, false, 23L, account1Nft1DissociateTimestamp, nftId1),
                tokenAccount(account2, true, 25L, 25L, nftId1),
                tokenAccount(account2, false, 25L, account2Nft1DissociateTimestamp, nftId1),
                tokenAccount(account1, true, 27L, 27L, nftId2),
                tokenAccount(account1, false, 27L, account1Nft2DissociateTimestamp, nftId2),
                tokenAccount(account2, true, 29L, 29L, nftId2),
                tokenAccount(account2, false, 29L, account2Nft2DissociateTimestamp, nftId2)
        );
        tokenAccountRepository.saveAll(tokenAccounts);

        // token dissociate transactions
        List<Transaction> transactions = List.of(
                tokenDissociateTransaction(account1Ft1DissociateTimestamp, account1),
                tokenDissociateTransaction(account1Nft1DissociateTimestamp, account1),
                tokenDissociateTransaction(account2Nft1DissociateTimestamp, account2),
                tokenDissociateTransaction(account1Nft2DissociateTimestamp, account1),
                tokenDissociateTransaction(account2Nft2DissociateTimestamp, account2)
        );
        transactionRepository.saveAll(transactions);

        // transfers
        tokenTransferRepository.saveAll(List.of(
                new TokenTransfer(account1Ft1DissociateTimestamp, -10, ftId1, account1),
                new TokenTransfer(account2Nft1DissociateTimestamp, -1, nftId1, account2)
        ));

        // nfts
        // - 2 for <account1, nftId1>, 1 already deleted before dissociate, the other without dissociate transfer
        // - 2 for <account1, nftId2>, 1 already deleted before dissociate, the other without dissociate transfer
        // - 2 for <account2, nftId1>, 1 already deleted before dissociate, the other with dissociate transfer
        // - 1 for <account2, nftId2>, already deleted, account2 dissociated nftId2 before nft class deletion
        // - 1 for <account1, nftId3>
        // - 1 for <account2, nftId3>
        nftRepository.saveAll(List.of(
                nft(account1, 25L, true, 27L, 1L, nftId1),
                nft(account1, 25L, false, 25L, 2L, nftId1),
                nft(account1, 30L, true, 35L, 1L, nftId2),
                nft(account1, 30L, false, 30L, 2L, nftId2),
                nft(account1, 40L, false, 40L, 1L, nftId3),
                nft(account2, 28L, true, 32L, 3L, nftId1),
                nft(account2, 28L, false, 28L, 4L, nftId1),
                nft(account2, 33L, true, 37L, 3L, nftId2),
                nft(account2, 45L, false, 45L, 2L, nftId3)
        ));

        // nft transfers from nft class treasury update
        nftTransferRepository.save(
                nftTransfer(40L, NEW_TREASURY, TREASURY, NftTransferId.WILDCARD_SERIAL_NUMBER,nftId3)
        );

        // expected token changes
        ftClass1.setTotalSupply(ftClass1.getTotalSupply() - 10);
        ftClass1.setModifiedTimestamp(account1Ft1DissociateTimestamp);
        // 1 nft wiped from explicit token transfer of the token dissociate, 1 wiped from a previous token dissociate
        // without explicit token transfer
        nftClass1.setTotalSupply(nftClass1.getTotalSupply() - 2);
        nftClass1.setModifiedTimestamp(account2Nft1DissociateTimestamp);
        nftClass2.setTotalSupply(nftClass2.getTotalSupply() - 1);
        nftClass2.setModifiedTimestamp(account1Nft2DissociateTimestamp);

        // when
        migrate();

        // then
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrder(
                nft(account1, 25L, true, 27L, 1L, nftId1),
                nft(account1, 25L, true, account1Nft1DissociateTimestamp, 2L, nftId1),
                nft(account1, 30L, true, 35L, 1L, nftId2),
                nft(account1, 30L, true, account1Nft2DissociateTimestamp, 2L, nftId2),
                nft(account1, 40L, false, 40L, 1L, nftId3),
                nft(account2, 28L, true, 32L, 3L, nftId1),
                nft(account2, 28L, true, account2Nft1DissociateTimestamp, 4L, nftId1),
                nft(account2, 33L, true, 37L, 3L, nftId2),
                nft(account2, 45L, false, 45L, 2L, nftId3)
        );
        // expect new nft transfers from token dissociate of deleted nft class
        // expect nft transfers for nft treasury update removed
        assertThat(nftTransferRepository.findAll()).containsExactlyInAnyOrder(
                nftTransfer(account1Nft1DissociateTimestamp, null, account1, 2L, nftId1),
                nftTransfer(account1Nft2DissociateTimestamp, null, account1, 2L, nftId2),
                nftTransfer(account2Nft1DissociateTimestamp, null, account2, 4L, nftId1)
        );
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenAccounts);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrder(ftClass1, ftClass2, nftClass1, nftClass2,
                nftClass3);
        // the token transfer for nft should have been removed
        assertThat(tokenTransferRepository.findAll()).containsExactlyInAnyOrder(
                new TokenTransfer(account1Ft1DissociateTimestamp, -10, ftId1, account1)
        );
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.execute(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private Entity entity(Token token) {
        return entity(token, false, token.getCreatedTimestamp());
    }

    private Entity entity(Token token, boolean deleted, long modifiedTimestamp) {
        Entity entity = token.getTokenId().getTokenId().toEntity();
        entity.setCreatedTimestamp(token.getCreatedTimestamp());
        entity.setDeleted(deleted);
        entity.setMemo("");
        entity.setModifiedTimestamp(modifiedTimestamp);
        return entity;
    }

    private Nft nft(EntityId accountId, long createdTimestamp, boolean deleted, long modifiedTimestamp,
            long serialNumber, EntityId tokenId) {
        Nft nft = new Nft(serialNumber, tokenId);
        nft.setAccountId(accountId);
        nft.setCreatedTimestamp(createdTimestamp);
        nft.setDeleted(deleted);
        nft.setMetadata(new byte[]{1});
        nft.setModifiedTimestamp(modifiedTimestamp);
        return nft;
    }

    private NftTransfer nftTransfer(long consensusTimestamp, EntityId receiver, EntityId sender, long serialNumber,
            EntityId tokenId) {
        NftTransfer nftTransfer = new NftTransfer();
        nftTransfer.setId(new NftTransferId(consensusTimestamp, serialNumber, tokenId));
        nftTransfer.setReceiverAccountId(receiver);
        nftTransfer.setSenderAccountId(sender);
        return nftTransfer;
    }

    private Token token(long createdTimestamp, EntityId tokenId, TokenTypeEnum tokenType) {
        Token token = Token.of(tokenId);
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(0);
        token.setFreezeDefault(false);
        token.setInitialSupply(0L);
        token.setModifiedTimestamp(createdTimestamp);
        token.setName("foo");
        token.setTotalSupply(1_000_000L);
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol("bar");
        token.setTreasuryAccountId(TREASURY);
        token.setType(tokenType);
        return token;
    }

    private TokenAccount tokenAccount(EntityId accountId, boolean associated, long createdTimestamp,
            long modifiedTimestamp, EntityId tokenId) {
        TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, modifiedTimestamp);
        tokenAccount.setAssociated(associated);
        tokenAccount.setAutomaticAssociation(false);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        tokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        return tokenAccount;
    }

    private Transaction tokenDissociateTransaction(long consensusNs, EntityId payer) {
        Transaction transaction = new Transaction();
        transaction.setConsensusNs(consensusNs);
        transaction.setEntityId(payer);
        transaction.setPayerAccountId(payer);
        transaction.setResult(22);
        transaction.setType(TRANSACTION_TYPE_TOKEN_DISSOCIATE);
        transaction.setValidStartNs(consensusNs - 5);
        return transaction;
    }
}
