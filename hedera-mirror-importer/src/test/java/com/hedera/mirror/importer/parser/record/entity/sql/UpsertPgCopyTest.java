package com.hedera.mirror.importer.parser.record.entity.sql;

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
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceUtils;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Nft;
import com.hedera.mirror.importer.domain.NftId;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenId;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenSupplyTypeEnum;
import com.hedera.mirror.importer.domain.TokenTypeEnum;
import com.hedera.mirror.importer.parser.UpsertPgCopy;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.repository.upsert.EntityUpsertQueryGenerator;
import com.hedera.mirror.importer.repository.upsert.NftUpsertQueryGenerator;
import com.hedera.mirror.importer.repository.upsert.ScheduleUpsertQueryGenerator;
import com.hedera.mirror.importer.repository.upsert.TokenAccountUpsertQueryGenerator;
import com.hedera.mirror.importer.repository.upsert.TokenUpsertQueryGenerator;

class UpsertPgCopyTest extends IntegrationTest {

    private static final String KEY = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    @Resource
    EntityUpsertQueryGenerator entityUpsertQueryGenerator;
    @Resource
    NftUpsertQueryGenerator nftUpsertQueryGenerator;
    @Resource
    ScheduleUpsertQueryGenerator scheduleUpsertQueryGenerator;
    @Resource
    TokenUpsertQueryGenerator tokenUpsertQueryGenerator;
    @Resource
    TokenAccountUpsertQueryGenerator tokenAccountUpsertQueryGenerator;
    @Resource
    private DataSource dataSource;
    @Resource
    private EntityRepository entityRepository;
    @Resource
    private NftRepository nftRepository;
    @Resource
    private TokenRepository tokenRepository;
    @Resource
    private TokenAccountRepository tokenAccountRepository;
    @Resource
    private ScheduleRepository scheduleRepository;
    private UpsertPgCopy<Entity> entityPgCopy;
    private UpsertPgCopy<Nft> nftPgCopy;
    private UpsertPgCopy<Schedule> schedulePgCopy;
    private UpsertPgCopy<TokenAccount> tokenAccountPgCopy;
    private UpsertPgCopy<Token> tokenPgCopy;

    @Resource
    private RecordParserProperties recordParserProperties;

    @BeforeEach
    void beforeEach() {
        entityPgCopy = new UpsertPgCopy<>(Entity.class, meterRegistry, recordParserProperties,
                entityUpsertQueryGenerator);
        nftPgCopy = new UpsertPgCopy<>(Nft.class, meterRegistry, recordParserProperties,
                nftUpsertQueryGenerator);
        schedulePgCopy = new UpsertPgCopy<>(Schedule.class, meterRegistry, recordParserProperties,
                scheduleUpsertQueryGenerator);
        tokenAccountPgCopy = new UpsertPgCopy<>(TokenAccount.class, meterRegistry, recordParserProperties,
                tokenAccountUpsertQueryGenerator);
        tokenPgCopy = new UpsertPgCopy<>(Token.class, meterRegistry, recordParserProperties,
                tokenUpsertQueryGenerator);
    }

    @Test
    void entityInsertOnly() throws SQLException {
        var entities = new HashSet<Entity>();
        long consensusTimestamp = 1;
        entities.add(getEntity(1, consensusTimestamp, consensusTimestamp, "memo-1"));
        entities.add(getEntity(2, consensusTimestamp, consensusTimestamp, null));
        entities.add(getEntity(3, consensusTimestamp, consensusTimestamp, "memo-3"));
        entities.add(getEntity(4, consensusTimestamp, consensusTimestamp, ""));

        copyWithTransactionSupport(entityPgCopy, entities);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrderElementsOf(entities);
    }

    @Test
    void entityInsertAndUpdate() throws SQLException {
        var entities = new HashSet<Entity>();
        long consensusTimestamp = 1;
        entities.add(getEntity(1, consensusTimestamp, consensusTimestamp, "memo-1"));
        entities.add(getEntity(2, consensusTimestamp, consensusTimestamp, "memo-2"));
        entities.add(getEntity(3, consensusTimestamp, consensusTimestamp, "memo-3"));
        entities.add(getEntity(4, consensusTimestamp, consensusTimestamp, "memo-4"));

        copyWithTransactionSupport(entityPgCopy, entities);

        assertThat(entityRepository.findAll()).containsExactlyInAnyOrderElementsOf(entities);

        // update
        entities.clear();
        long updateTimestamp = 5;

        // updated
        entities.add(getEntity(3, null, updateTimestamp, ""));
        entities.add(getEntity(4, null, updateTimestamp, "updated-memo-4"));

        // new inserts
        entities.add(getEntity(5, null, updateTimestamp, "memo-5"));
        entities.add(getEntity(6, null, updateTimestamp, "memo-6"));

        copyWithTransactionSupport(entityPgCopy, entities); // copy inserts and updates

        assertThat(entityRepository
                .findAll())
                .isNotEmpty()
                .hasSize(6)
                .extracting(Entity::getMemo)
                .containsExactlyInAnyOrder("memo-1", "memo-2", "", "updated-memo-4", "memo-5", "memo-6");
    }

    @Test
    void entityInsertAndUpdateBatched() throws SQLException {
        var entities = new HashSet<Entity>();
        long consensusTimestamp = 1;
        entities.add(getEntity(1, consensusTimestamp, consensusTimestamp, "memo-1"));
        entities.add(getEntity(2, consensusTimestamp, consensusTimestamp, "memo-2"));
        entities.add(getEntity(3, consensusTimestamp, consensusTimestamp, "memo-3"));
        entities.add(getEntity(4, consensusTimestamp, consensusTimestamp, "memo-4"));

        // update
        long updateTimestamp = 5;

        // updated
        var updateEntities = new HashSet<Entity>();
        updateEntities.add(getEntity(3, null, updateTimestamp, ""));
        updateEntities.add(getEntity(4, null, updateTimestamp, "updated-memo-4"));

        // new inserts
        updateEntities.add(getEntity(5, null, updateTimestamp, "memo-5"));
        updateEntities.add(getEntity(6, null, updateTimestamp, "memo-6"));

        copyWithTransactionSupport(entityPgCopy, entities, updateEntities); // copy inserts and updates

        assertThat(entityRepository
                .findAll())
                .isNotEmpty()
                .hasSize(6)
                .extracting(Entity::getMemo)
                .containsExactlyInAnyOrder("memo-1", "memo-2", "", "updated-memo-4", "memo-5", "memo-6");
    }

    @Test
    void tokenInsertOnly() throws SQLException, DecoderException {
        var tokens = new HashSet<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L));
        tokens.add(getToken("0.0.3000", "0.0.1001", 2L));
        tokens.add(getToken("0.0.4000", "0.0.1001", 3L));
        tokens.add(getToken("0.0.5000", "0.0.1001", 4L));

        copyWithTransactionSupport(tokenPgCopy, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);
    }

    @Test
    void tokenInsertAndUpdate() throws SQLException, DecoderException {
        // insert
        var tokens = new HashSet<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L));
        tokens.add(getToken("0.0.3000", "0.0.1002", 2L));
        tokens.add(getToken("0.0.4000", "0.0.1003", 3L));
        tokens.add(getToken("0.0.5000", "0.0.1004", 4L));

        copyWithTransactionSupport(tokenPgCopy, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        // updates
        tokens.clear();
        tokens.add(getToken("0.0.4000", "0.0.2001", null));
        tokens.add(getToken("0.0.5000", "0.0.2002", null));
        tokens.add(getToken("0.0.6000", "0.0.2005", 5L));
        tokens.add(getToken("0.0.7000", "0.0.2006", 6L));

        copyWithTransactionSupport(tokenPgCopy, tokens);

        assertThat(tokenRepository
                .findAll())
                .isNotEmpty()
                .hasSize(6)
                .extracting(Token::getTreasuryAccountId)
                .extracting(EntityId::entityIdToString)
                .containsExactlyInAnyOrder("0.0.1001", "0.0.1002", "0.0.2001", "0.0.2002", "0.0.2005", "0.0.2006");
    }

    @Test
    void tokenAccountInsertOnly() throws SQLException, DecoderException {
        // inserts token first
        var tokens = new HashSet<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L));
        tokens.add(getToken("0.0.2001", "0.0.1001", 2L));
        tokens.add(getToken("0.0.3000", "0.0.1001", 3L));

        copyWithTransactionSupport(tokenPgCopy, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        var tokenAccounts = new HashSet<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.1001", 1L, false, 1L));
        tokenAccounts.add(getTokenAccount("0.0.2001", "0.0.1001", 2L, false, 2L));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4001", 3L, false, 3L));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4002", 4L, false, 4L));

        copyWithTransactionSupport(tokenAccountPgCopy, tokenAccounts);

        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);
        assertThat(tokenAccountRepository
                .findAll())
                .isNotEmpty()
                .hasSize(4)
                .extracting(TokenAccount::getModifiedTimestamp)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void tokenAccountInsertFreezeStatus() throws SQLException, DecoderException {
        // inserts token first
        var tokens = new HashSet<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L, false, null, null));
        tokens.add(getToken("0.0.3000", "0.0.1001", 2L, true, Key.newBuilder()
                .setEd25519(ByteString.copyFrom(Hex.decodeHex(KEY))).build(), null));
        tokens.add(getToken("0.0.4000", "0.0.1001", 3L, false, Key.newBuilder()
                .setEd25519(ByteString.copyFrom(Hex.decodeHex(KEY))).build(), null));

        copyWithTransactionSupport(tokenPgCopy, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        // associate
        var tokenAccounts = new HashSet<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.2001", 5L, true, 5L));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.3001", 6L, true, 6L));
        tokenAccounts.add(getTokenAccount("0.0.4000", "0.0.4001", 7L, true, 7L));

        copyWithTransactionSupport(tokenAccountPgCopy, tokenAccounts);

        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);
        assertThat(tokenAccountRepository
                .findAll())
                .isNotEmpty()
                .hasSize(3)
                .extracting(TokenAccount::getFreezeStatus)
                .containsExactlyInAnyOrder(TokenFreezeStatusEnum.NOT_APPLICABLE, TokenFreezeStatusEnum.FROZEN,
                        TokenFreezeStatusEnum.UNFROZEN);

        // reverse freeze status
        tokenAccounts.clear();
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.3001", null, null, 10L,
                TokenFreezeStatusEnum.UNFROZEN, null));
        tokenAccounts.add(getTokenAccount("0.0.4000", "0.0.4001", null, null, 11L,
                TokenFreezeStatusEnum.FROZEN, null));

        copyWithTransactionSupport(tokenAccountPgCopy, tokenAccounts);

        assertThat(tokenAccountRepository
                .findAll())
                .isNotEmpty()
                .hasSize(3)
                .extracting(TokenAccount::getFreezeStatus)
                .containsExactlyInAnyOrder(TokenFreezeStatusEnum.NOT_APPLICABLE, TokenFreezeStatusEnum.UNFROZEN,
                        TokenFreezeStatusEnum.FROZEN);
    }

    @Test
    void tokenAccountInsertKycStatus() throws SQLException, DecoderException {
        // inserts token first
        var tokens = new HashSet<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L, false, null, null));
        tokens.add(getToken("0.0.3000", "0.0.1001", 2L, false, null, Key.newBuilder()
                .setEd25519(ByteString.copyFrom(Hex.decodeHex(KEY))).build()));

        copyWithTransactionSupport(tokenPgCopy, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        var tokenAccounts = new HashSet<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.2001", 5L, true, 5L));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.3001", 6L, true, 6L));

        copyWithTransactionSupport(tokenAccountPgCopy, tokenAccounts);

        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);
        assertThat(tokenAccountRepository
                .findAll())
                .isNotEmpty()
                .hasSize(2)
                .extracting(TokenAccount::getKycStatus)
                .containsExactlyInAnyOrder(TokenKycStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.REVOKED);

        // grant KYC
        tokenAccounts.clear();
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.3001", null, null, 11L,
                null, TokenKycStatusEnum.GRANTED));

        copyWithTransactionSupport(tokenAccountPgCopy, tokenAccounts);

        assertThat(tokenAccountRepository
                .findAll())
                .isNotEmpty()
                .hasSize(2)
                .extracting(TokenAccount::getKycStatus)
                .containsExactlyInAnyOrder(TokenKycStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.GRANTED);
    }

    @Test
    void tokenAccountInsertWithMissingToken() throws SQLException {
        var tokenAccounts = new HashSet<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.1001", 1L, false, 1L));
        tokenAccounts.add(getTokenAccount("0.0.2001", "0.0.1001", 2L, false, 2L));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4001", 3L, false, 3L));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4002", 4L, false, 4L));

        copyWithTransactionSupport(tokenAccountPgCopy, tokenAccounts);
        assertThat(tokenAccountRepository.findAll()).isEmpty();
    }

    @Test
    void tokenAccountInsertAndUpdate() throws SQLException, DecoderException {
        // inserts token first
        var tokens = new HashSet<Token>();
        tokens.add(getToken("0.0.2000", "0.0.1001", 1L));
        tokens.add(getToken("0.0.2001", "0.0.1001", 2L));
        tokens.add(getToken("0.0.3000", "0.0.1001", 3L));
        tokens.add(getToken("0.0.4000", "0.0.1001", 4L));

        copyWithTransactionSupport(tokenPgCopy, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        var tokenAccounts = new HashSet<TokenAccount>();
        tokenAccounts.add(getTokenAccount("0.0.2000", "0.0.1001", 5L, false,
                6L));
        tokenAccounts.add(getTokenAccount("0.0.2001", "0.0.1001", 6L, false,
                7L));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4001", 7L, false,
                8L));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4002", 8L, false,
                9L));

        copyWithTransactionSupport(tokenAccountPgCopy, tokenAccounts);

        // update
        tokenAccounts.clear();
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4001", null, true,
                10L));
        tokenAccounts.add(getTokenAccount("0.0.3000", "0.0.4002", null, true,
                11L));
        tokenAccounts.add(getTokenAccount("0.0.4000", "0.0.7001", 10L, true,
                12L));
        tokenAccounts.add(getTokenAccount("0.0.4000", "0.0.7002", 11L, true,
                13L));

        copyWithTransactionSupport(tokenAccountPgCopy, tokenAccounts);

        assertThat(tokenAccountRepository
                .findAll())
                .isNotEmpty()
                .hasSize(6)
                .extracting(TokenAccount::getModifiedTimestamp)
                .containsExactlyInAnyOrder(6L, 7L, 10L, 11L, 12L, 13L);
    }

    @Test
    void scheduleInsertOnly() throws SQLException {
        var schedules = new HashSet<Schedule>();
        schedules.add(getSchedule(1L, "0.0.1001", null));
        schedules.add(getSchedule(2L, "0.0.1002", null));
        schedules.add(getSchedule(3L, "0.0.1003", null));
        schedules.add(getSchedule(4L, "0.0.1004", null));

        copyWithTransactionSupport(schedulePgCopy, schedules);
        assertThat(scheduleRepository.findAll()).containsExactlyInAnyOrderElementsOf(schedules);
    }

    @Test
    void scheduleInsertAndUpdate() throws SQLException {
        var schedules = new HashSet<Schedule>();
        schedules.add(getSchedule(1L, "0.0.1001", null));
        schedules.add(getSchedule(2L, "0.0.1002", null));
        schedules.add(getSchedule(3L, "0.0.1003", null));
        schedules.add(getSchedule(4L, "0.0.1004", null));

        copyWithTransactionSupport(schedulePgCopy, schedules);
        assertThat(scheduleRepository.findAll()).containsExactlyInAnyOrderElementsOf(schedules);

        // update
        schedules.clear();

        schedules.add(getSchedule(null, "0.0.1003", 5L));
        schedules.add(getSchedule(null, "0.0.1004", 6L));
        schedules.add(getSchedule(7L, "0.0.1005", null));
        schedules.add(getSchedule(8L, "0.0.1006", null));

        copyWithTransactionSupport(schedulePgCopy, schedules);
        assertThat(scheduleRepository
                .findAll())
                .isNotEmpty()
                .hasSize(6)
                .extracting(Schedule::getExecutedTimestamp)
                .containsExactlyInAnyOrder(null, null, 5L, 6L, null, null);
    }

    @Test
    void nftInsertMissingToken() throws SQLException, DecoderException {
        // mint
        var nfts = new HashSet<Nft>();
        nfts.add(getNft("0.0.2000", 1, null, 1L, 1L, "nft1", false));
        nfts.add(getNft("0.0.3000", 2, null, 2L, 2L, "nft2", false));
        nfts.add(getNft("0.0.4000", 3, null, 3L, 3L, "nft3", false));
        nfts.add(getNft("0.0.5000", 4, null, 4L, 4L, "nft4", false));

        copyWithTransactionSupport(nftPgCopy, nfts);
        assertThat(nftRepository.findAll()).isEmpty();
    }

    @Test
    void nftMint() throws SQLException, DecoderException {
        // inserts tokens first
        var tokens = new HashSet<Token>();
        tokens.add(getToken("0.0.2000", "0.0.98", 1L));
        tokens.add(getToken("0.0.3000", "0.0.98", 2L));
        tokens.add(getToken("0.0.4000", "0.0.98", 3L));
        tokens.add(getToken("0.0.5000", "0.0.98", 4L));

        copyWithTransactionSupport(tokenPgCopy, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        // insert due to mint
        var nfts = new HashSet<Nft>();
        nfts.add(getNft("0.0.2000", 1, "0.0.1001", 10L, 10L, "nft1", false));
        nfts.add(getNft("0.0.3000", 2, "0.0.1002", 11L, 11L, "nft2", false));
        nfts.add(getNft("0.0.4000", 3, "0.0.1003", 12L, 12L, "nft3", false));
        nfts.add(getNft("0.0.5000", 4, "0.0.1004", 13L, 13L, "nft4", false));

        copyWithTransactionSupport(nftPgCopy, nfts);

        assertThat(nftRepository
                .findAll())
                .isNotEmpty()
                .hasSize(4)
                .extracting(Nft::getAccountId)
                .extracting(EntityId::entityIdToString)
                .containsExactlyInAnyOrder("0.0.1001", "0.0.1002", "0.0.1003", "0.0.1004");
    }

    @Test
    void nftInsertAndUpdate() throws SQLException, DecoderException {
        // inserts tokens first
        var tokens = new HashSet<Token>();
        tokens.add(getToken("0.0.2000", "0.0.98", 1L));
        tokens.add(getToken("0.0.3000", "0.0.98", 2L));
        tokens.add(getToken("0.0.4000", "0.0.98", 3L));
        tokens.add(getToken("0.0.5000", "0.0.98", 4L));
        tokens.add(getToken("0.0.6000", "0.0.98", 5L));
        tokens.add(getToken("0.0.7000", "0.0.98", 6L));

        copyWithTransactionSupport(tokenPgCopy, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        // insert due to mint
        var nfts = new HashSet<Nft>();
        nfts.add(getNft("0.0.2000", 1, "0.0.1001", 10L, 10L, "nft1", false));
        nfts.add(getNft("0.0.3000", 2, "0.0.1002", 11L, 11L, "nft2", false));
        nfts.add(getNft("0.0.4000", 3, "0.0.1003", 12L, 12L, "nft3", false));
        nfts.add(getNft("0.0.5000", 4, "0.0.1004", 13L, 13L, "nft4", false));

        copyWithTransactionSupport(nftPgCopy, nfts);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrderElementsOf(nfts);

        // updates with transfer
        nfts.clear();
        nfts.add(getNft("0.0.4000", 3, "0.0.1013", null, 15L, null, null));
        nfts.add(getNft("0.0.5000", 4, "0.0.1014", null, 16L, null, null));
        nfts.add(getNft("0.0.6000", 5, "0.0.1015", 17L, 17L, "nft5", false));
        nfts.add(getNft("0.0.7000", 6, "0.0.1016", 18L, 18L, "nft6", false));

        copyWithTransactionSupport(nftPgCopy, nfts);

        assertThat(nftRepository
                .findAll())
                .isNotEmpty()
                .hasSize(6)
                .extracting(Nft::getAccountId)
                .extracting(EntityId::entityIdToString)
                .containsExactlyInAnyOrder("0.0.1001", "0.0.1002", "0.0.1013", "0.0.1014", "0.0.1015", "0.0.1016");
    }

    @Test
    void nftInsertTransferBurnWipe() throws SQLException, DecoderException {
        // inserts tokens first
        var tokens = new HashSet<Token>();
        tokens.add(getToken("0.0.2000", "0.0.98", 1L));
        tokens.add(getToken("0.0.3000", "0.0.98", 2L));
        tokens.add(getToken("0.0.4000", "0.0.98", 3L));
        tokens.add(getToken("0.0.5000", "0.0.98", 4L));

        copyWithTransactionSupport(tokenPgCopy, tokens);
        assertThat(tokenRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokens);

        // insert due to mint
        var nfts = new HashSet<Nft>();
        nfts.add(getNft("0.0.2000", 1, "0.0.1001", 10L, 10L, "nft1", false));
        nfts.add(getNft("0.0.3000", 2, "0.0.1002", 11L, 11L, "nft2", false));
        nfts.add(getNft("0.0.4000", 3, "0.0.1003", 12L, 12L, "nft3", false));
        nfts.add(getNft("0.0.5000", 4, "0.0.1004", 13L, 13L, "nft4", false));

        copyWithTransactionSupport(nftPgCopy, nfts);
        assertThat(nftRepository.findAll()).containsExactlyInAnyOrderElementsOf(nfts);

        // updates with mint transfers
        nfts.clear();
        nfts.add(getNft("0.0.2000", 1, "0.0.1005", null, 15L, null, false));
        nfts.add(getNft("0.0.3000", 2, "0.0.1006", null, 16L, null, false));
        nfts.add(getNft("0.0.4000", 3, "0.0.1007", null, 17L, null, false));
        nfts.add(getNft("0.0.5000", 4, "0.0.1008", null, 18L, null, false));

        copyWithTransactionSupport(nftPgCopy, nfts);
        assertThat(nftRepository
                .findAll())
                .isNotEmpty()
                .hasSize(4)
                .extracting(Nft::getAccountId)
                .extracting(EntityId::entityIdToString)
                .containsExactlyInAnyOrder("0.0.1005", "0.0.1006", "0.0.1007", "0.0.1008");

        // updates with wipe/burn
        nfts.clear();
        nfts.add(getNft("0.0.3000", 2, "0.0.0", null, 21L, null, true));
        nfts.add(getNft("0.0.5000", 4, "0.0.0", null, 23L, null, true));
        copyWithTransactionSupport(nftPgCopy, nfts);

        assertThat(nftRepository
                .findAll())
                .isNotEmpty()
                .hasSize(4)
                .extracting(Nft::getDeleted)
                .containsExactlyInAnyOrder(false, true, false, true);
    }

    private void copyWithTransactionSupport(UpsertPgCopy upsertPgCopy, Collection... items) throws SQLException {
        try (Connection connection = DataSourceUtils.getConnection(dataSource)) {
            connection.setAutoCommit(false); // for tests have to set auto commit to false or temp table gets lost
            upsertPgCopy.init(connection);
            for (Collection batch : items) {
                upsertPgCopy.copy(batch, connection);
            }
            connection.commit();
        } finally {

        }
    }

    private Entity getEntity(long id, Long createdTimestamp, long modifiedTimestamp, String memo) {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setCreatedTimestamp(createdTimestamp);
        entity.setModifiedTimestamp(modifiedTimestamp);
        entity.setNum(id);
        entity.setRealm(0L);
        entity.setShard(0L);
        entity.setType(1);
        entity.setMemo(memo);
        return entity;
    }

    private Token getToken(String tokenId, String treasuryAccountId, Long createdTimestamp) throws DecoderException {
        return getToken(tokenId, treasuryAccountId, createdTimestamp, false, null, null);
    }

    private Token getToken(String tokenId, String treasuryAccountId, Long createdTimestamp,
                           Boolean freezeDefault, Key freezeKey, Key kycKey) throws DecoderException {
        var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
        var hexKey = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(instr))).build().toByteArray();
        Token token = new Token();
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(1000);
        token.setFreezeDefault(freezeDefault);
        token.setFreezeKey(freezeKey != null ? freezeKey.toByteArray() : null);
        token.setInitialSupply(1_000_000_000L);
        token.setKycKey(kycKey != null ? kycKey.toByteArray() : null);
        token.setMaxSupply(1_000_000_000L);
        token.setModifiedTimestamp(3L);
        token.setName("FOO COIN TOKEN" + tokenId);
        token.setSupplyKey(hexKey);
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol("FOOTOK" + tokenId);
        token.setTokenId(new TokenId(EntityId.of(tokenId, EntityTypeEnum.TOKEN)));
        token.setTreasuryAccountId(EntityId.of(treasuryAccountId, ACCOUNT));
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setWipeKey(hexKey);
        return token;
    }

    private TokenAccount getTokenAccount(String tokenId, String accountId, Long createdTimestamp, Boolean associated,
                                         Long modifiedTimestamp) {
        return getTokenAccount(tokenId, accountId, createdTimestamp, associated, modifiedTimestamp, null, null);
    }

    private TokenAccount getTokenAccount(String tokenId, String accountId, Long createdTimestamp, Boolean associated,
                                         Long modifiedTimestamp, TokenFreezeStatusEnum freezeStatus,
                                         TokenKycStatusEnum kycStatus) {
        TokenAccount tokenAccount = new TokenAccount(EntityId
                .of(tokenId, EntityTypeEnum.TOKEN), EntityId.of(accountId, ACCOUNT));
        tokenAccount.setAssociated(associated);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        tokenAccount.setModifiedTimestamp(modifiedTimestamp);

        return tokenAccount;
    }

    private Schedule getSchedule(Long createdTimestamp, String scheduleId, Long executedTimestamp) {
        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(createdTimestamp);
        schedule.setCreatorAccountId(EntityId.of("0.0.123", EntityTypeEnum.ACCOUNT));
        schedule.setExecutedTimestamp(executedTimestamp);
        schedule.setPayerAccountId(EntityId.of("0.0.456", EntityTypeEnum.ACCOUNT));
        schedule.setScheduleId(EntityId.of(scheduleId, EntityTypeEnum.SCHEDULE));
        schedule.setTransactionBody("transaction body".getBytes());
        return schedule;
    }

    private Nft getNft(String tokenId, long serialNumber, String accountId, Long createdTimestamp,
                       long modifiedTimeStamp, String metadata, Boolean deleted) {
        Nft nft = new Nft();
        nft.setAccountId(accountId == null ? null : EntityId.of(accountId, EntityTypeEnum.ACCOUNT));
        nft.setCreatedTimestamp(createdTimestamp);
        nft.setDeleted(deleted);
        nft.setId(new NftId(serialNumber, EntityId.of(tokenId, EntityTypeEnum.TOKEN)));
        nft.setMetadata(metadata == null ? null : metadata.getBytes(StandardCharsets.UTF_8));
        nft.setModifiedTimestamp(modifiedTimeStamp);
        return nft;
    }
}
