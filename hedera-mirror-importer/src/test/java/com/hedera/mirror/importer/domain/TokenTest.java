package com.hedera.mirror.importer.domain;

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
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.util.EntityIdEndec;

public class TokenTest {

    private final EntityId FOO_COIN_ID = EntityId.of("0.0.101", EntityTypeEnum.TOKEN);
    String key = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
    private final EntityId treasuryAccount = EntityId.of("0.0.102", EntityTypeEnum.ACCOUNT);

    @Test
    void createValidToken() throws DecoderException {
        Token token = token(1);
        assertAll(
                () -> assertNotNull(token.getFeeScheduleKeyEd25519Hex()),
                () -> assertNotNull(token.getFreezeKeyEd25519Hex()),
                () -> assertNotNull(token.getKycKeyEd25519Hex()),
                () -> assertNotNull(token.getSupplyKeyEd25519Hex()),
                () -> assertNotNull(token.getWipeKeyEd25519Hex()),
                () -> assertNotEquals(0, token.getTotalSupply())
        );
    }

    @Test
    void createTokenWithBadKeys() {
        Token token = new Token();
        byte[] badBytes = "badkey".getBytes();
        token.setFeeScheduleKey(badBytes);
        token.setFreezeKey(badBytes);
        token.setKycKey(badBytes);
        token.setSupplyKey(badBytes);
        token.setWipeKey(badBytes);

        assertNull(token.getFeeScheduleKeyEd25519Hex());
        assertNull(token.getFreezeKeyEd25519Hex());
        assertNull(token.getKycKeyEd25519Hex());
        assertNull(token.getSupplyKeyEd25519Hex());
        assertNull(token.getWipeKeyEd25519Hex());
    }

    @Test
    void nullCharacters() {
        Token token = new Token();
        token.setName("abc" + (char) 0);
        token.setSymbol("abc" + (char) 0);
        assertThat(token.getName()).isEqualTo("abc�");
        assertThat(token.getSymbol()).isEqualTo("abc�");
    }

    @Test
    void of() {
        EntityId tokenId = EntityIdEndec.decode(1057, EntityTypeEnum.TOKEN);
        Token expected = new Token();
        expected.setTokenId(new TokenId(tokenId));

        Token actual = Token.of(tokenId);

        assertThat(actual).isEqualTo(expected);
    }

    private Token token(long consensusTimestamp) throws DecoderException {
        var hexKey = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(key))).build().toByteArray();
        Token token = new Token();
        token.setCreatedTimestamp(consensusTimestamp);
        token.setDecimals(1000);
        token.setFeeScheduleKey(hexKey);
        token.setFreezeDefault(false);
        token.setFreezeKey(hexKey);
        token.setInitialSupply(1_000_000_000L);
        token.setKycKey(hexKey);
        token.setModifiedTimestamp(3L);
        token.setName("FOO COIN TOKEN");
        token.setSupplyKey(hexKey);
        token.setSymbol("FOOTOK");
        token.setTokenId(new TokenId(FOO_COIN_ID));
        token.setTreasuryAccountId(treasuryAccount);
        token.setWipeKey(hexKey);
        return token;
    }
}
