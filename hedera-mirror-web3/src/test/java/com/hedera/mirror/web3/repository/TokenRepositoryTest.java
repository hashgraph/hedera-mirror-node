package com.hedera.mirror.web3.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
        assertThat(tokenRepository.findFreezeDefault(tokenId).orElse(false)).isTrue();
    }

    @Test
    void findKycDefault() {
        assertThat(tokenRepository.findKycKey(tokenId).orElse(new byte[] {})).isEqualTo(token.getKycKey());
    }
}
