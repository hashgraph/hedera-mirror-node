/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.graphql.mapper;

import static org.mapstruct.MappingInheritanceStrategy.AUTO_INHERIT_FROM_CONFIG;

import com.hedera.mirror.graphql.viewmodel.Entity;
import org.mapstruct.MapperConfig;
import org.mapstruct.Mapping;

@MapperConfig(mappingInheritanceStrategy = AUTO_INHERIT_FROM_CONFIG, uses = CommonMapper.class)
public interface EntityMapper<T extends Entity> {

    @Mapping(
            expression = "java(com.hedera.mirror.common.util.DomainUtils.bytesToHex(source.getAlias()))",
            target = "alias")
    @Mapping(source = "ethereumNonce", target = "nonce")
    @Mapping(source = "id", target = "entityId")
    @Mapping(source = "timestampRange", target = "timestamp")
    T map(com.hedera.mirror.common.domain.entity.Entity source);
}
