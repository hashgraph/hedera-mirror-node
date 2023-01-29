package com.hedera.mirror.common.domain.token;

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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;

class TokenTest {

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
        TokenId tokenId = new TokenId(EntityId.of(1, EntityType.TOKEN));
        Token token = new Token();
        token.setTokenId(tokenId);
        Token actual = Token.of(tokenId.getTokenId());
        assertThat(actual).isNotSameAs(token).extracting(Token::getTokenId).isEqualTo(tokenId);
    }
}
