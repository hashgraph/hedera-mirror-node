package com.hedera.mirror.importer.repository;

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
import static org.assertj.core.api.Assertions.from;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import javax.annotation.Resource;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Token;
import com.hedera.mirror.importer.domain.TokenSupplyTypeEnum;
import com.hedera.mirror.importer.domain.TokenTypeEnum;

public class TokenRepositoryTest extends AbstractRepositoryTest {
    @Resource
    protected TokenRepository tokenRepository;

    private static final EntityId FOO_COIN_ID = EntityId.of("0.0.101", EntityTypeEnum.TOKEN);
    private static final String key = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
    private static final EntityId treasuryAccount = EntityId.of("0.0.102", EntityTypeEnum.ACCOUNT);
    private static final long INITIAL_SUPPLY = 1_000_000L;

    @Test
    void save() throws DecoderException {
        Token token = tokenRepository.save(token(1));
        assertThat(tokenRepository.findById(token.getTokenId())
                .get()).isEqualTo(token);
    }

    @Test
    void nullCharacter() throws DecoderException {
        Token token = token(1);
        token.setName("abc" + (char) 0);
        token.setSymbol("abc" + (char) 0);
        tokenRepository.save(token);
        assertThat(tokenRepository.findById(token.getTokenId())).get().isEqualTo(token);
    }

    @Test
    void updateSupply() throws DecoderException {
        Token token = tokenRepository.save(token(1));
        long newTotalSupply = INITIAL_SUPPLY - 1000;
        long modifiedTimestamp = 5L;
        tokenRepository.updateTokenSupply(token.getTokenId(), newTotalSupply, modifiedTimestamp);
        assertThat(tokenRepository.findById(token.getTokenId()).get())
                .returns(modifiedTimestamp, from(Token::getModifiedTimestamp))
                .returns(newTotalSupply, from(Token::getTotalSupply));
    }

    @Test
    void updateSupplyOnMissingToken() throws DecoderException {
        long createdTimestamp = 1L;
        Token token = tokenRepository.save(token(createdTimestamp));
        long newTotalSupply = INITIAL_SUPPLY - 1000;
        long modifiedTimestamp = 5L;
        Token.Id missingTokenId = new Token.Id(EntityId.of("0.0.555", EntityTypeEnum.TOKEN));
        tokenRepository.updateTokenSupply(missingTokenId, newTotalSupply, modifiedTimestamp);
        assertThat(tokenRepository.findById(token.getTokenId()).get())
                .returns(createdTimestamp, from(Token::getModifiedTimestamp))
                .returns(INITIAL_SUPPLY, from(Token::getTotalSupply));
    }

    private Token token(long consensusTimestamp) throws DecoderException {
        var hexKey = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(key))).build().toByteArray();
        Token token = new Token();
        token.setCreatedTimestamp(consensusTimestamp);
        token.setDecimals(1000);
        token.setFreezeDefault(false);
        token.setFreezeKey(hexKey);
        token.setInitialSupply(INITIAL_SUPPLY);
        token.setKycKey(hexKey);
        token.setModifiedTimestamp(consensusTimestamp);
        token.setName("FOO COIN TOKEN");
        token.setSupplyKey(hexKey);
        token.setSupplyType(TokenSupplyTypeEnum.INFINITE);
        token.setSymbol("FOOTOK");
        token.setTokenId(new Token.Id(FOO_COIN_ID));
        token.setTotalSupply(INITIAL_SUPPLY);
        token.setTreasuryAccountId(treasuryAccount);
        token.setType(TokenTypeEnum.FUNGIBLE_COMMON);
        token.setWipeKey(hexKey);
        return token;
    }
}
