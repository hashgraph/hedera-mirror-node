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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.CustomFee;
import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenAccountId;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenId;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenSupplyTypeEnum;
import com.hedera.mirror.importer.domain.TokenTypeEnum;
import com.hedera.mirror.importer.parser.PgCopy;
import com.hedera.mirror.importer.parser.balance.AccountBalanceFileParser;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TokenRepository;
import com.hedera.mirror.importer.util.EntityIdEndec;

@EnabledIfV1
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.43.1")
class AddMissingTokenAccountAssociationMigrationTest extends IntegrationTest {

    private static final EntityId COLLECTOR_1 = EntityId.of(0, 0, 2001, EntityTypeEnum.ACCOUNT);
    private static final EntityId COLLECTOR_2 = EntityId.of(0, 0, 2002, EntityTypeEnum.ACCOUNT);
    private static final EntityId COLLECTOR_3 = EntityId.of(0, 0, 2003, EntityTypeEnum.ACCOUNT);
    private static final EntityId NODE = EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT);
    private static final EntityId TREASURY = EntityId.of(0, 0, 2004, EntityTypeEnum.ACCOUNT);
    private static final long EXISTING_TOKEN_CREATE_TIMESTAMP = 50;
    private static final long NEW_TOKEN_CREATE_TIMESTAMP = 100;
    private static final long LATEST_TOKEN_BALANCE_TIMESTAMP = 500;
    private static final long PREVIOUS_TOKEN_BALANCE_TIMESTAMP = 400;
    private static final EntityId EXISTING_TOKEN = EntityId.of(0, 0, 1001, EntityTypeEnum.TOKEN);
    private static final EntityId NEW_TOKEN = EntityId.of(0, 0, 1002, EntityTypeEnum.TOKEN);
    private static final byte[] KEY = Key.newBuilder().setEd25519(ByteString.copyFromUtf8("key")).build().toByteArray();

    @Resource
    private AccountBalanceFileParser accountBalanceFileParser;

    private PgCopy<CustomFee> customFeePgCopy;

    @Resource
    private DataSource dataSource;

    @Resource
    private JdbcOperations jdbcOperations;

    @Resource
    private MeterRegistry meterRegistry;

    @Resource
    private RecordParserProperties parserProperties;

    private String migrationSql;

    @Value("classpath:db/migration/v1/V1.43.2__add_missing_token_account_association.sql")
    private File migrationSqlFile;

    @Resource
    private RecordFileRepository recordFileRepository;

    @Resource
    private TokenRepository tokenRepository;

    @Value("classpath:db/scripts/undo_v1.43.2.sql")
    private File undoSql;

    @Value("${hedera.mirror.importer.db.username}")
    private String username;

    @BeforeEach
    void beforeEach() throws IOException {
        customFeePgCopy = new PgCopy<>(CustomFee.class, meterRegistry, parserProperties);
        migrationSql = FileUtils.readFileToString(migrationSqlFile, "UTF-8").replace("${db-user}", username);
    }

    @AfterEach
    void afterEach() throws IOException {
        jdbcOperations.update(FileUtils.readFileToString(undoSql, "UTF-8"));
    }

    @ParameterizedTest
    @CsvSource({
            ", true, false, false, false, NOT_APPLICABLE, NOT_APPLICABLE",
            "450, true, false, true, false, UNFROZEN, NOT_APPLICABLE",
            "450, true, true, true, false, UNFROZEN, NOT_APPLICABLE",
            "500, true, true, true, true, UNFROZEN, GRANTED",
            "501, false, true, true, true, UNFROZEN, GRANTED"
    })
    void verify(Long lastTransactionConsensusTimestamp, boolean expectAdded, boolean freezeDefault,
                boolean freezeKey, boolean kycKey, TokenFreezeStatusEnum expectedFreezeStatus,
                TokenKycStatusEnum expectedKycStatus) {
        // given
        // at time of the new token creation:
        //     collector1 is a fixed fee collector who collects fee in the new token and the existing token
        //     collector2 is a fixed fee collector who collects fee in the new token and hbar
        //     collector3 is a fractional fee collector
        // at some time between PREVIOUS_TOKEN_BALANCE_TIMESTAMP and LATEST_TOKEN_BALANCE_TIMESTAMP, collector1
        // dissociated himself with the new token. Note there won't be an associated=false token account record for
        // (NEW_TOKEN, COLLECTOR_1), however, the dissociation is reflected in the latest account token balance
        customFeePgCopy.copy(List.of(
                fixedFee(10, COLLECTOR_1, NEW_TOKEN_CREATE_TIMESTAMP, NEW_TOKEN, NEW_TOKEN),
                fixedFee(5, COLLECTOR_1, NEW_TOKEN_CREATE_TIMESTAMP, EXISTING_TOKEN, NEW_TOKEN),
                fixedFee(15, COLLECTOR_2, NEW_TOKEN_CREATE_TIMESTAMP, NEW_TOKEN, NEW_TOKEN),
                fixedFee(15, COLLECTOR_2, NEW_TOKEN_CREATE_TIMESTAMP, null, NEW_TOKEN),
                fractionalFee(2, COLLECTOR_3, NEW_TOKEN_CREATE_TIMESTAMP, 7, null, 0, NEW_TOKEN)
        ), DataSourceUtils.getConnection(dataSource));
        tokenRepository.saveAll(List.of(
                token(EXISTING_TOKEN_CREATE_TIMESTAMP, false, false, false, EXISTING_TOKEN),
                token(NEW_TOKEN_CREATE_TIMESTAMP, freezeDefault, freezeKey, kycKey, NEW_TOKEN)
        ));
        List<TokenAccount> tokenAccountList = Lists.newArrayList(
                tokenAccount(COLLECTOR_1, true, EXISTING_TOKEN_CREATE_TIMESTAMP + 1, EXISTING_TOKEN)
        );
        persistTokenAccounts(tokenAccountList);

        // build account balance file
        long consensusTimestamp = PREVIOUS_TOKEN_BALANCE_TIMESTAMP;
        AccountBalanceFile previousAccountBalanceFile = AccountBalanceFile.builder()
                .consensusTimestamp(consensusTimestamp)
                .count(4L)
                .fileHash("hash")
                .items(Flux.just(
                        accountBalance(COLLECTOR_1, consensusTimestamp, EXISTING_TOKEN, NEW_TOKEN),
                        accountBalance(COLLECTOR_2, consensusTimestamp, NEW_TOKEN),
                        accountBalance(COLLECTOR_3, consensusTimestamp, NEW_TOKEN),
                        accountBalance(TREASURY, consensusTimestamp, EXISTING_TOKEN, NEW_TOKEN)
                ))
                .loadStart(100L)
                .name(accountBalanceFilename(consensusTimestamp))
                .nodeAccountId(NODE)
                .build();
        consensusTimestamp = LATEST_TOKEN_BALANCE_TIMESTAMP;
        AccountBalanceFile latestAccountBalanceFile = AccountBalanceFile.builder()
                .consensusTimestamp(consensusTimestamp)
                .count(4L)
                .fileHash("hash")
                .items(Flux.just(
                        accountBalance(COLLECTOR_1, consensusTimestamp, EXISTING_TOKEN),
                        accountBalance(COLLECTOR_2, consensusTimestamp, NEW_TOKEN),
                        accountBalance(COLLECTOR_3, consensusTimestamp, NEW_TOKEN),
                        accountBalance(TREASURY, consensusTimestamp, EXISTING_TOKEN, NEW_TOKEN)
                ))
                .loadStart(200L)
                .name(accountBalanceFilename(consensusTimestamp))
                .nodeAccountId(NODE)
                .build();

        // if there is no record file or the last transaction consensus timestamp at time of migration is not after
        // LATEST_TOKEN_BALANCE_TIMESTAMP, the db function should be triggered to add missing associations;
        // otherwise, if the last transaction consensus timestamp is after LATEST_TOKEN_BALANCE_TIMESTAMP,
        // the db function should run but won't add missing associations.
        RecordFile recordFile = recordFile(lastTransactionConsensusTimestamp);
        if (recordFile != null) {
            recordFileRepository.save(recordFile);
        }

        // when
        accountBalanceFileParser.parse(previousAccountBalanceFile);
        jdbcOperations.execute(migrationSql);
        accountBalanceFileParser.parse(latestAccountBalanceFile);

        // then
        if (expectAdded) {
            tokenAccountList.add(tokenAccount(TREASURY, true, EXISTING_TOKEN_CREATE_TIMESTAMP, EXISTING_TOKEN));
            tokenAccountList.add(tokenAccount(COLLECTOR_2, true, NEW_TOKEN_CREATE_TIMESTAMP,
                    expectedFreezeStatus, expectedKycStatus, NEW_TOKEN));
            tokenAccountList.add(tokenAccount(COLLECTOR_3, true, NEW_TOKEN_CREATE_TIMESTAMP,
                    expectedFreezeStatus, expectedKycStatus, NEW_TOKEN));
            tokenAccountList.add(tokenAccount(TREASURY, true, NEW_TOKEN_CREATE_TIMESTAMP,
                    expectedFreezeStatus, expectedKycStatus, NEW_TOKEN));
        }
        assertThat(retrieveTokenAccounts()).containsExactlyInAnyOrderElementsOf(tokenAccountList);
    }

    private AccountBalance accountBalance(EntityId accountId, long consensusTimestamp, EntityId... tokens) {
        AccountBalance accountBalance = new AccountBalance();
        accountBalance.setBalance(1000L);
        accountBalance.setTokenBalances(
                Arrays.stream(tokens)
                        .map(token -> new TokenBalance(0L, new TokenBalance.Id(consensusTimestamp, accountId, token)))
                        .collect(Collectors.toList())
        );
        accountBalance.setId(new AccountBalance.Id(consensusTimestamp, accountId));
        return accountBalance;
    }

    private String accountBalanceFilename(long consensusTimestamp) {
        Instant instant = Instant.ofEpochSecond(0, consensusTimestamp);
        return StreamFilename.getFilename(StreamType.BALANCE, StreamFilename.FileType.DATA, instant);
    }

    private List<TokenAccount> retrieveTokenAccounts() {
        return jdbcOperations.query("select * from token_account", new RowMapper<TokenAccount>() {

            @Override
            public TokenAccount mapRow(ResultSet rs, int rowNum) throws SQLException {
                EntityId tokenId = EntityIdEndec.decode(rs.getLong("token_id"), EntityTypeEnum.TOKEN);
                EntityId accountId = EntityIdEndec.decode(rs.getLong("account_id"), EntityTypeEnum.TOKEN);
                TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, rs.getLong("modified_timestamp"));
                tokenAccount.setAssociated(rs.getBoolean("associated"));
                tokenAccount.setCreatedTimestamp(rs.getLong("created_timestamp"));
                tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.values()[rs.getShort("freeze_status")]);
                tokenAccount.setKycStatus(TokenKycStatusEnum.values()[rs.getShort("kyc_status")]);
                return tokenAccount;
            }
        });
    }

    private void persistTokenAccounts(List<TokenAccount> tokenAccounts) {
        String sql = "insert into token_account (token_id, account_id, associated, created_timestamp, freeze_status, " +
                "kyc_status, modified_timestamp) values (?,?,?,?,?,?,?)";
        jdbcOperations.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TokenAccount tokenAccount = tokenAccounts.get(i);
                ps.setLong(1, tokenAccount.getId().getTokenId().getId());
                ps.setLong(2, tokenAccount.getId().getAccountId().getId());
                ps.setBoolean(3, tokenAccount.getAssociated());
                ps.setLong(4, tokenAccount.getCreatedTimestamp());
                ps.setShort(5, (short)tokenAccount.getFreezeStatus().ordinal());
                ps.setShort(6, (short)tokenAccount.getKycStatus().ordinal());
                ps.setLong(7, tokenAccount.getId().getModifiedTimestamp());
            }

            @Override
            public int getBatchSize() {
                return tokenAccounts.size();
            }
        });
    }

    private RecordFile recordFile(Long lastTransactionConsensusTimestamp) {
        if (lastTransactionConsensusTimestamp == null) {
            return null;
        }

        Instant instant = Instant.ofEpochSecond(0, lastTransactionConsensusTimestamp);
        return RecordFile.builder()
                .consensusEnd(lastTransactionConsensusTimestamp)
                .consensusStart(lastTransactionConsensusTimestamp - 1)
                .count(2L)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash("hash")
                .hash("hash")
                .index(10L)
                .loadEnd(2002L)
                .loadStart(2000L)
                .name(StreamFilename.getFilename(StreamType.RECORD, StreamFilename.FileType.DATA, instant))
                .nodeAccountId(NODE)
                .previousHash("hash")
                .version(5)
                .build();
    }

    private Token token(long createdTimestamp, boolean freezeDefault, boolean freezeKey, boolean kycKey,
                        EntityId tokenId) {
        Token token = new Token();
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(5);
        token.setFreezeDefault(freezeDefault);
        token.setInitialSupply(100L);
        token.setMaxSupply(Long.MAX_VALUE);
        token.setModifiedTimestamp(createdTimestamp);
        token.setName("TOKEN");
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol("TOKEN");
        token.setTreasuryAccountId(TREASURY);
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setTokenId(new TokenId(tokenId));

        if (freezeKey) {
            token.setFreezeKey(KEY);
        }

        if (kycKey) {
            token.setKycKey(KEY);
        }

        return token;
    }

    private TokenAccount tokenAccount(EntityId accountId, boolean associated, long createdTimestamp, EntityId tokenId) {
        return tokenAccount(accountId, associated, createdTimestamp, TokenFreezeStatusEnum.NOT_APPLICABLE,
                TokenKycStatusEnum.NOT_APPLICABLE, tokenId);
    }

    private TokenAccount tokenAccount(EntityId accountId, boolean associated, long createdTimestamp,
                                      TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus,
                                      EntityId tokenId) {
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setAssociated(associated);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setId(new TokenAccountId(tokenId, accountId, createdTimestamp));
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        return tokenAccount;
    }

    private CustomFee fixedFee(long amount, EntityId collectorAccountId, long createdTimestamp,
                               EntityId denominatingTokenId, EntityId tokenId) {
        CustomFee customFee = new CustomFee();
        customFee.setAmount(amount);
        customFee.setCollectorAccountId(collectorAccountId);
        customFee.setCreatedTimestamp(createdTimestamp);
        customFee.setDenominatingTokenId(denominatingTokenId);
        customFee.setTokenId(tokenId);
        return customFee;
    }

    private CustomFee fractionalFee(long denominator, EntityId collectorAccountId, long createdTimestamp,
                                    long numerator, Long maximum, long minimum, EntityId tokenId) {
        CustomFee customFee = new CustomFee();
        customFee.setAmount(numerator);
        customFee.setAmountDenominator(denominator);
        customFee.setCollectorAccountId(collectorAccountId);
        customFee.setCreatedTimestamp(createdTimestamp);
        customFee.setMaximumAmount(maximum);
        customFee.setMinimumAmount(minimum);
        customFee.setTokenId(tokenId);
        return customFee;
    }
}
