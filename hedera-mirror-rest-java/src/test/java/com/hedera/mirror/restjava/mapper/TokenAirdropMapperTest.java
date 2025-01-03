/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.mapper;

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.rest.model.TimestampRange;
import com.hedera.mirror.rest.model.TokenAirdrop;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TokenAirdropMapperTest {

    private CommonMapper commonMapper;
    private DomainBuilder domainBuilder;
    private TokenAirdropMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        mapper = new TokenAirdropMapperImpl(commonMapper);
        domainBuilder = new DomainBuilder();
    }

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void map(TokenTypeEnum tokenType) {
        var tokenAirdrop = domainBuilder.tokenAirdrop(tokenType).get();
        var to = commonMapper.mapTimestamp(tokenAirdrop.getTimestampLower());

        assertThat(mapper.map(List.of(tokenAirdrop)))
                .first()
                .returns(tokenType == NON_FUNGIBLE_UNIQUE ? null : tokenAirdrop.getAmount(), TokenAirdrop::getAmount)
                .returns(EntityId.of(tokenAirdrop.getReceiverAccountId()).toString(), TokenAirdrop::getReceiverId)
                .returns(EntityId.of(tokenAirdrop.getSenderAccountId()).toString(), TokenAirdrop::getSenderId)
                .returns(
                        tokenType == FUNGIBLE_COMMON ? null : tokenAirdrop.getSerialNumber(),
                        TokenAirdrop::getSerialNumber)
                .returns(EntityId.of(tokenAirdrop.getTokenId()).toString(), TokenAirdrop::getTokenId)
                .satisfies(a -> assertThat(a.getTimestamp())
                        .returns(to, TimestampRange::getFrom)
                        .returns(null, TimestampRange::getTo));
    }

    @Test
    void mapNulls() {
        var tokenAirdrop = new com.hedera.mirror.common.domain.token.TokenAirdrop();
        assertThat(mapper.map(tokenAirdrop))
                .returns(null, TokenAirdrop::getAmount)
                .returns(null, TokenAirdrop::getReceiverId)
                .returns(null, TokenAirdrop::getSenderId)
                .returns(null, TokenAirdrop::getSerialNumber)
                .returns(null, TokenAirdrop::getTokenId)
                .returns(null, TokenAirdrop::getTimestamp);
    }
}
