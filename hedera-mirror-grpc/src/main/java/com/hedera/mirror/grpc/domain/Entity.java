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

package com.hedera.mirror.grpc.domain;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityType;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Builder
@Data
@jakarta.persistence.Entity
@NoArgsConstructor
@AllArgsConstructor
public class Entity {
    @Id
    private Long id;

    private Long num;

    private Long realm;

    private Long shard;

    @Type(PostgreSQLGuavaRangeType.class)
    private Range<Long> timestampRange;

    @Enumerated(EnumType.STRING)
    @Type(PostgreSQLEnumType.class)
    private EntityType type;
}
