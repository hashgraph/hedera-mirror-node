package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(tokenRepository.findById(token.getTokenId())
                .get()).isEqualTo(token);
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
}
