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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenId;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;
import com.hedera.mirror.importer.domain.TokenSupplyTypeEnum;
import com.hedera.mirror.importer.domain.TokenTypeEnum;
import com.hedera.mirror.importer.repository.TokenAccountRepository;
import com.hedera.mirror.importer.repository.TokenBalanceRepository;
import com.hedera.mirror.importer.repository.TokenRepository;

@EnabledIfV1
class FixTokenTreasuryAssociationTest extends IntegrationTest {

    private static final long CREATE_TIMESTAMP = 10001;
    private static final long TOKEN_OWNER_ASSOCIATION_TIMESTAMP = CREATE_TIMESTAMP + 10;
    private static final byte[] KEY = Key.newBuilder().setEd25519(ByteString.copyFromUtf8("key")).build().toByteArray();
    private static final EntityId TOKEN = EntityId.of(0 ,0, 1001, EntityTypeEnum.TOKEN);
    private static final long TOKEN_BALANCE_TIMESTAMP = CREATE_TIMESTAMP + 500;
    private static final EntityId TREASURY = EntityId.of(0 ,0, 2001, EntityTypeEnum.ACCOUNT);
    private static final EntityId TOKEN_OWNER = EntityId.of(0 ,0, 2002, EntityTypeEnum.ACCOUNT);

    @Value("classpath:db/scripts/fixTokenTreasuryAssociation.sql")
    private File sqlScript;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private TokenAccountRepository tokenAccountRepository;

    @Resource
    private TokenBalanceRepository tokenBalanceRepository;

    @Resource
    private TokenRepository tokenRepository;

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideMissingTokenTreasuryAssociationArguments")
    void missingTokenTreasuryAssociation(String name, Token token, TokenBalance tokenBalance,
                                         TokenAccount expectedTokenAccount) throws IOException {
        // given
        tokenRepository.save(token);
        tokenBalanceRepository.save(tokenBalance);

        // TOKEN_OWNER associates himself with TOKEN at TOKEN_OWNER_ASSOCIATION_TIMESTAMP
        var tokenAccount = createTokenAccount(TOKEN_OWNER, TOKEN_OWNER_ASSOCIATION_TIMESTAMP,
                TokenFreezeStatusEnum.NOT_APPLICABLE, TokenKycStatusEnum.NOT_APPLICABLE, TOKEN);
        tokenAccountRepository.save(tokenAccount);

        // when
        int count = runScript();

        // then
        assertThat(count).isEqualTo(1);
        assertThat(tokenAccountRepository.findById(expectedTokenAccount.getId())).get().isEqualTo(expectedTokenAccount);
    }

    private int runScript() throws IOException {
        return jdbcTemplate.update(FileUtils.readFileToString(sqlScript, "UTF-8"));
    }

    private static Stream<Arguments> provideMissingTokenTreasuryAssociationArguments() {
        var tokenBalance = new TokenBalance(100, new TokenBalance.Id(TOKEN_BALANCE_TIMESTAMP, TREASURY, TOKEN));
        return Stream.of(
                Arguments.of(
                        "freezeDefault false, no freezeKey, no kycKey",
                        createToken(CREATE_TIMESTAMP, false, false, false, TOKEN, TREASURY),
                        tokenBalance,
                        createTokenAccount(TREASURY, CREATE_TIMESTAMP, TokenFreezeStatusEnum.NOT_APPLICABLE,
                                TokenKycStatusEnum.NOT_APPLICABLE, TOKEN)
                ),
                Arguments.of(
                        "freezeDefault false, has freezeKey, no kycKey",
                        createToken(CREATE_TIMESTAMP, false, true, false, TOKEN, TREASURY),
                        tokenBalance,
                        createTokenAccount(TREASURY, CREATE_TIMESTAMP, TokenFreezeStatusEnum.UNFROZEN,
                                TokenKycStatusEnum.NOT_APPLICABLE, TOKEN)
                ),
                Arguments.of(
                        "freezeDefault true, has freezeKey, no kycKey",
                        createToken(CREATE_TIMESTAMP, true, true, false, TOKEN, TREASURY),
                        tokenBalance,
                        createTokenAccount(TREASURY, CREATE_TIMESTAMP, TokenFreezeStatusEnum.UNFROZEN,
                                TokenKycStatusEnum.NOT_APPLICABLE, TOKEN)
                ),
                Arguments.of(
                        "freezeDefault true, has freezeKey, has kycKey",
                        createToken(CREATE_TIMESTAMP, true, true, true, TOKEN, TREASURY),
                        tokenBalance,
                        createTokenAccount(TREASURY, CREATE_TIMESTAMP, TokenFreezeStatusEnum.UNFROZEN,
                                TokenKycStatusEnum.GRANTED, TOKEN)
                )
        );
    }

    private static Token createToken(long createdTimestamp, boolean freezeDefault, boolean freezeKey, boolean kycKey,
            EntityId tokenId, EntityId treasury) {
        var token = new Token();
        token.setCreatedTimestamp(createdTimestamp);
        token.setDecimals(10);
        token.setFreezeDefault(freezeDefault);
        token.setInitialSupply(0L);
        token.setMaxSupply(1_000_000_000);
        token.setModifiedTimestamp(createdTimestamp);
        token.setName("TOKEN");
        token.setSymbol("SYMBOL");
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setTreasuryAccountId(treasury);
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

    private static TokenAccount createTokenAccount(EntityId accountId, long createdTimestamp,
                                                   TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus,
                                                   EntityId tokenId) {
        var tokenAccount = new TokenAccount(tokenId, accountId);
        tokenAccount.setAssociated(true);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setFreezeStatus(freezeStatus);
        tokenAccount.setKycStatus(kycStatus);
        tokenAccount.setModifiedTimestamp(createdTimestamp);
        return tokenAccount;
    }
}
