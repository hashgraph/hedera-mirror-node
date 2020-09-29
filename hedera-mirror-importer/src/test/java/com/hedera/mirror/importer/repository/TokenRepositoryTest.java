package com.hedera.mirror.importer.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import javax.annotation.Resource;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Token;

public class TokenRepositoryTest extends AbstractRepositoryTest {
    @Resource
    protected TokenRepository tokenRepository;

    private final EntityId FOO_COIN_ID = EntityId.of("0.0.101", EntityTypeEnum.TOKEN);
    String key = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
    private final EntityId treasuryAccount = EntityId.of("0.0.102", EntityTypeEnum.ACCOUNT);

    @Test
    void save() throws DecoderException {
        Token token = tokenRepository.save(token(1));
        tokenMatch(token, tokenRepository.findById(token.getTokenId())
                .get());
    }

    private Token token(long consensusTimestamp) throws DecoderException {
        var hexKey = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decodeHex(key))).build().toByteArray();
        Token token = new Token();
        token.setCreatedTimestamp(1L);
        token.setDecimals(1000);
        token.setFreezeDefault(false);
        token.setFreezeKey(hexKey);
        token.setInitialSupply(1_000_000_000L);
        token.setKycKey(hexKey);
        token.setModifiedTimestamp(3L);
        token.setName("FOO COIN TOKEN");
        token.setSupplyKey(hexKey);
        token.setSymbol("FOOTOK");
        token.setTokenId(new Token.Id(FOO_COIN_ID));
        token.setTreasuryAccountId(treasuryAccount);
        token.setWipeKey(hexKey);
        return token;
    }

    private void tokenMatch(Token expected, Token actual) {
        assertAll(
                () -> assertNotNull(actual),
                () -> assertEquals(expected.getCreatedTimestamp(), actual.getCreatedTimestamp()),
                () -> assertEquals(expected.getDecimals(), actual.getDecimals()),
                () -> assertArrayEquals(expected.getFreezeKey(), actual.getFreezeKey()),
                () -> assertEquals(expected.getInitialSupply(), actual.getInitialSupply()),
                () -> assertArrayEquals(expected.getKycKey(), actual.getKycKey()),
                () -> assertEquals(expected.getModifiedTimestamp(), actual.getModifiedTimestamp()),
                () -> assertEquals(expected.getName(), actual.getName()),
                () -> assertArrayEquals(expected.getSupplyKey(), actual.getSupplyKey()),
                () -> assertEquals(expected.getSymbol(), actual.getSymbol()),
                () -> assertArrayEquals(expected.getWipeKey(), actual.getWipeKey()),
                () -> assertEquals(expected.getFreezeKeyEd25519Hex(), actual.getFreezeKeyEd25519Hex()),
                () -> assertEquals(expected.getKycKeyEd25519Hex(), actual.getKycKeyEd25519Hex()),
                () -> assertEquals(expected.getSupplyKeyEd25519Hex(), actual.getSupplyKeyEd25519Hex()),
                () -> assertEquals(expected.getWipeKeyEd25519Hex(), actual.getWipeKeyEd25519Hex())
        );
    }
}
