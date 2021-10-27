package com.hedera.mirror.grpc.domain;

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

import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TypeDef;

@Builder
@Data
@javax.persistence.Entity
@NoArgsConstructor
@AllArgsConstructor
@TypeDef(
        defaultForType = Range.class,
        typeClass = PostgreSQLGuavaRangeType.class
)
@TypeDef(
        defaultForType = EntityType.class,
        typeClass = PostgreSQLEnumType.class
)
public class Entity {
    @Id
    private Long id;

    private Long num;

    private Long realm;

    private Long shard;

    private Range<Long> timestampRange;

    @Enumerated(EnumType.STRING)
    private EntityType type;
}
