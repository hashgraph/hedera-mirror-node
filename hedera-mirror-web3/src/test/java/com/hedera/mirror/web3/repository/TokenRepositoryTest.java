package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.web3.Web3IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenRepositoryTest extends Web3IntegrationTest {
    private final TokenRepository tokenRepository;
    private long tokenId;
    private Token token;

    @BeforeEach
    void persistToken() {
        token = domainBuilder.token()
                .customize(t -> t.type(NON_FUNGIBLE_UNIQUE).freezeDefault(true)).persist();
        tokenId = token.getTokenId().getTokenId().getId();
    }

    @Test
    void findName() {
        assertThat(tokenRepository.findName(tokenId).orElse("")).isEqualTo(token.getName());
    }

    @Test
    void findSymbol() {
        assertThat(tokenRepository.findSymbol(tokenId).orElse("")).isEqualTo(token.getSymbol());
    }

    @Test
    void findTotalSupply() {
        assertThat(tokenRepository.findTotalSupply(tokenId).orElse(0L)).isEqualTo(token.getTotalSupply());
    }

    @Test
    void findDecimals() {
        assertThat(tokenRepository.findDecimals(tokenId).orElse(0)).isEqualTo(token.getDecimals());
    }

    @Test
    void findType() {
        assertThat(tokenRepository.findType(tokenId).orElse(FUNGIBLE_COMMON)).isEqualTo(token.getType());
    }

    @Test
    void findFreezeDefault() {
        assertThat(tokenRepository.findFreezeDefault(tokenId)).isTrue();
    }

    @Test
    void findKycDefault() {
        assertThat(tokenRepository.findKycDefault(tokenId).orElse(new byte[] {})).isEqualTo(token.getKycKey());
    }
}
