package com.hedera.mirror.importer.db;

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
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.CustomFee;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
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
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import com.hedera.mirror.importer.repository.TokenRepository;

@EnabledIfV1
class AddMissingTokenAccountAssociationTest extends IntegrationTest {

    private static final EntityId COLLECTOR_1 = EntityId.of(0, 0, 2001, EntityTypeEnum.ACCOUNT);
    private static final EntityId COLLECTOR_2 = EntityId.of(0, 0, 2002, EntityTypeEnum.ACCOUNT);
    private static final EntityId COLLECTOR_3 = EntityId.of(0, 0, 2003, EntityTypeEnum.ACCOUNT);
    private static final EntityId TREASURY = EntityId.of(0, 0, 2004, EntityTypeEnum.ACCOUNT);
    private static final long EXISTING_TOKEN_CREATE_TIMESTAMP = 50;
    private static final long NEW_TOKEN_CREATE_TIMESTAMP = 100;
    private static final long LATEST_TOKEN_BALANCE_TIMESTAMP = 500;
    private static final long PREVIOUS_TOKEN_BALANCE_TIMESTAMP = 400;
    private static final EntityId EXISTING_TOKEN = EntityId.of(0, 0, 1001, EntityTypeEnum.TOKEN);
    private static final EntityId NEW_TOKEN = EntityId.of(0, 0, 1002, EntityTypeEnum.TOKEN);
    private static final byte[] KEY = Key.newBuilder().setEd25519(ByteString.copyFromUtf8("key")).build().toByteArray();

    private PgCopy<CustomFee> customFeePgCopy;

    @Resource
    private DataSource dataSource;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    MeterRegistry meterRegistry;

    @Resource
    private RecordParserProperties parserProperties;

    @Value("classpath:db/scripts/addMissingTokenAccountAssociation.sql")
    private File sqlScript;

    @Resource
    private TokenAccountRepository tokenAccountRepository;

    @Resource
    private TokenBalanceRepository tokenBalanceRepository;

    @Resource
    private TokenRepository tokenRepository;

    @BeforeEach
    void beforeEach() {
        customFeePgCopy = new PgCopy<>(CustomFee.class, meterRegistry, parserProperties);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideArguments")
    void verify(String name, boolean freezeDefault, boolean freezeKey, boolean kycKey,
                TokenFreezeStatusEnum expectedFreezeStatus, TokenKycStatusEnum expectedKycStatus) throws IOException {
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
        tokenBalanceRepository.saveAll(List.of(
                // previous token balance snapshot
                new TokenBalance(0, new TokenBalance.Id(PREVIOUS_TOKEN_BALANCE_TIMESTAMP, COLLECTOR_1, EXISTING_TOKEN)),
                new TokenBalance(100, new TokenBalance.Id(PREVIOUS_TOKEN_BALANCE_TIMESTAMP, TREASURY, EXISTING_TOKEN)),
                new TokenBalance(0, new TokenBalance.Id(PREVIOUS_TOKEN_BALANCE_TIMESTAMP, COLLECTOR_1, NEW_TOKEN)),
                new TokenBalance(0, new TokenBalance.Id(PREVIOUS_TOKEN_BALANCE_TIMESTAMP, COLLECTOR_2, NEW_TOKEN)),
                new TokenBalance(0, new TokenBalance.Id(PREVIOUS_TOKEN_BALANCE_TIMESTAMP, COLLECTOR_3, NEW_TOKEN)),
                new TokenBalance(100, new TokenBalance.Id(PREVIOUS_TOKEN_BALANCE_TIMESTAMP, TREASURY, NEW_TOKEN)),
                // latest token balance snapshot
                new TokenBalance(0, new TokenBalance.Id(LATEST_TOKEN_BALANCE_TIMESTAMP, COLLECTOR_1, EXISTING_TOKEN)),
                new TokenBalance(100, new TokenBalance.Id(LATEST_TOKEN_BALANCE_TIMESTAMP, TREASURY, EXISTING_TOKEN)),
                new TokenBalance(0, new TokenBalance.Id(LATEST_TOKEN_BALANCE_TIMESTAMP, COLLECTOR_2, NEW_TOKEN)),
                new TokenBalance(0, new TokenBalance.Id(LATEST_TOKEN_BALANCE_TIMESTAMP, COLLECTOR_3, NEW_TOKEN)),
                new TokenBalance(100, new TokenBalance.Id(LATEST_TOKEN_BALANCE_TIMESTAMP, TREASURY, NEW_TOKEN))
        ));
        List<TokenAccount> tokenAccountList = Lists.newArrayList(
                tokenAccount(TREASURY, true, EXISTING_TOKEN_CREATE_TIMESTAMP, EXISTING_TOKEN),
                tokenAccount(COLLECTOR_1, true, EXISTING_TOKEN_CREATE_TIMESTAMP + 1, EXISTING_TOKEN)
        );
        tokenAccountRepository.saveAll(tokenAccountList);

        // when
        int count = runScript();

        // then
        assertThat(count).isEqualTo(3);
        tokenAccountList.add(tokenAccount(COLLECTOR_2, true, NEW_TOKEN_CREATE_TIMESTAMP,
                expectedFreezeStatus, expectedKycStatus, NEW_TOKEN));
        tokenAccountList.add(tokenAccount(COLLECTOR_3, true, NEW_TOKEN_CREATE_TIMESTAMP,
                expectedFreezeStatus, expectedKycStatus, NEW_TOKEN));
        tokenAccountList.add(tokenAccount(TREASURY, true, NEW_TOKEN_CREATE_TIMESTAMP,
                expectedFreezeStatus, expectedKycStatus, NEW_TOKEN));
        assertThat(tokenAccountRepository.findAll()).containsExactlyInAnyOrderElementsOf(tokenAccountList);
    }

    private int runScript() throws IOException {
        return jdbcTemplate.update(FileUtils.readFileToString(sqlScript, "UTF-8"));
    }

    private static Stream<Arguments> provideArguments() {
        return Stream.of(
                Arguments.of("freezeDefault false, no freezeKey, no kycKey", false, false, false,
                        TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE),
                Arguments.of("freezeDefault false, has freezeKey, no kycKey", false, true, false,
                        TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.NOT_APPLICABLE),
                Arguments.of("freezeDefault true, has freezeKey, no kycKey", true, true, false,
                        TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.NOT_APPLICABLE),
                Arguments.of("freezeDefault true, has freezeKey, has kycKey", true, true, true,
                        TokenFreezeStatusEnum.UNFROZEN, TokenKycStatusEnum.GRANTED)
        );
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
            TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus, EntityId tokenId) {
        TokenAccount tokenAccount = new TokenAccount();
        tokenAccount.setAssociated(associated);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setId(new TokenAccountId(tokenId, accountId));
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        tokenAccount.setModifiedTimestamp(createdTimestamp);
        return tokenAccount;
    }

    private CustomFee fixedFee(long amount, EntityId collectorAccountId, long createdTimestamp,
            EntityId denominatingTokenId, EntityId tokenId) {
        CustomFee customFee = new CustomFee();
        customFee.setAmount(amount);
        customFee.setCollectorAccountId(collectorAccountId);
        customFee.setDenominatingTokenId(denominatingTokenId);
        customFee.setId(new CustomFee.Id(createdTimestamp, tokenId));
        return customFee;
    }

    private CustomFee fractionalFee(long denominator, EntityId collectorAccountId, long createdTimestamp,
            long numerator, Long maximum, long minimum, EntityId tokenId) {
        CustomFee customFee = new CustomFee();
        customFee.setAmount(numerator);
        customFee.setAmountDenominator(denominator);
        customFee.setCollectorAccountId(collectorAccountId);
        customFee.setId(new CustomFee.Id(createdTimestamp, tokenId));
        customFee.setMaximumAmount(maximum);
        customFee.setMinimumAmount(minimum);
        return customFee;
    }
}
