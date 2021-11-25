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

import org.junit.jupiter.api.Test;

class TokenTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Test
    void createValidToken() {
        Token token = domainBuilder.token().get();
        assertNotEquals(0, token.getTotalSupply());
    }

    @Test
    void nullCharacters() {
        Token token = domainBuilder.token().get();
        token.setName("abc" + (char) 0);
        token.setSymbol("abc" + (char) 0);
        assertThat(token.getName()).isEqualTo("abc�");
        assertThat(token.getSymbol()).isEqualTo("abc�");
    }

    @Test
    void of() {
        Token token = domainBuilder.token().get();
        TokenId tokenId = token.getTokenId();
        Token actual = Token.of(tokenId.getTokenId());
        assertThat(actual).isNotSameAs(token).extracting(Token::getTokenId).isEqualTo(tokenId);
    }
}
