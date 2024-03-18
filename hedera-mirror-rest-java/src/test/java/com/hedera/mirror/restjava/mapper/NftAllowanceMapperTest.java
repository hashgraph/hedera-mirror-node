/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.NftAllowance;
import com.hedera.mirror.rest.model.TimestampRange;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NftAllowanceMapperTest {

    private NftAllowanceMapper mapper;
    private DomainBuilder domainBuilder;

    @BeforeEach
    void setup() {
        mapper = new NftAllowanceMapperImpl(new CommonMapperImpl());
        domainBuilder = new DomainBuilder();
    }

    @Test
    void map() {
        var allowance = domainBuilder.nftAllowance().get();

        assertThat(mapper.map(List.of(allowance)))
                .first()
                .returns(EntityId.of(allowance.getOwner()).toString(), NftAllowance::getOwner)
                .returns(EntityId.of(allowance.getTokenId()).toString(), NftAllowance::getTokenId)
                .returns(EntityId.of(allowance.getSpender()).toString(), NftAllowance::getSpender)
                .returns(allowance.isApprovedForAll(), NftAllowance::getApprovedForAll)
                .satisfies(a -> assertThat(a.getTimestamp())
                        .returns(
                                String.valueOf(allowance.getTimestampLower() / Math.pow(10, 9)),
                                TimestampRange::getFrom)
                        .returns(null, TimestampRange::getTo));
    }

    @Test
    void mapNulls() {
        var allowance = new com.hedera.mirror.common.domain.entity.NftAllowance();

        assertThat(mapper.map(allowance))
                .returns(null, NftAllowance::getOwner)
                .returns(null, NftAllowance::getTokenId)
                .returns(null, NftAllowance::getSpender)
                .returns(false, NftAllowance::getApprovedForAll)
                .returns(null, NftAllowance::getTimestamp);
    }
}
