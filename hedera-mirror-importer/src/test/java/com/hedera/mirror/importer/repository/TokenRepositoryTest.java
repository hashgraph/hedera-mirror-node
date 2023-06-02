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

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenId;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

class TokenRepositoryTest extends AbstractRepositoryTest {

    @Resource
    protected TokenRepository tokenRepository;

    private static final EntityId FOO_COIN_ID = EntityId.of("0.0.101", EntityType.TOKEN);
    private static final String key = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
    private static final EntityId treasuryAccount = EntityId.of("0.0.102", EntityType.ACCOUNT);
    private static final long INITIAL_SUPPLY = 1_000_000L;

    @Test
    void save() {
        Token token = tokenRepository.save(token(1));
        assertThat(tokenRepository.findById(token.getTokenId())).get().isEqualTo(token);
    }

    @Test
    void nullCharacter() {
        Token token = token(1);
        token.setName("abc" + (char) 0);
        token.setSymbol("abc" + (char) 0);
        tokenRepository.save(token);
        assertThat(tokenRepository.findById(token.getTokenId())).get().isEqualTo(token);
    }

    @SneakyThrows
    private Token token(long consensusTimestamp) {
        var hexKey = Key.newBuilder()
                .setEd25519(ByteString.copyFrom(Hex.decodeHex(key)))
                .build()
                .toByteArray();
        Token token = new Token();
        token.setCreatedTimestamp(consensusTimestamp);
        token.setDecimals(1000);
        token.setFreezeDefault(false);
        token.setFreezeKey(hexKey);
        token.setInitialSupply(INITIAL_SUPPLY);
        token.setKycKey(hexKey);
        token.setModifiedTimestamp(consensusTimestamp);
        token.setName("FOO COIN TOKEN");
        token.setPauseKey(hexKey);
        token.setPauseStatus(TokenPauseStatusEnum.PAUSED);
        token.setSupplyKey(hexKey);
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol("FOOTOK");
        token.setTokenId(new TokenId(FOO_COIN_ID));
        token.setTotalSupply(INITIAL_SUPPLY);
        token.setTreasuryAccountId(treasuryAccount);
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setWipeKey(hexKey);
        return token;
    }
}
