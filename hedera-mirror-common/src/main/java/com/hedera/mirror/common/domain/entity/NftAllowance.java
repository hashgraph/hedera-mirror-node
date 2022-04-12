package com.hedera.mirror.common.domain.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.Serializable;
import javax.persistence.Convert;
import javax.persistence.IdClass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.TypeDef;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.RangeToStringDeserializer;
import com.hedera.mirror.common.converter.RangeToStringSerializer;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.Upsertable;

@Data
@javax.persistence.Entity
@IdClass(NftAllowance.Id.class)
@NoArgsConstructor
@SuperBuilder
@TypeDef(
        defaultForType = Range.class,
        typeClass = PostgreSQLGuavaRangeType.class
)
@Upsertable(history = true)
public class NftAllowance implements History {

    private boolean approvedForAll;

    @javax.persistence.Id
    private long owner;

    @Convert(converter = AccountIdConverter.class)
    private EntityId payerAccountId;

    @javax.persistence.Id
    private long spender;

    @JsonDeserialize(using = RangeToStringDeserializer.class)
    @JsonSerialize(using = RangeToStringSerializer.class)
    private Range<Long> timestampRange;

    @javax.persistence.Id
    private long tokenId;

    @JsonIgnore
    public NftAllowance.Id getId() {
        Id id = new Id();
        id.setOwner(owner);
        id.setSpender(spender);
        id.setTokenId(tokenId);
        return id;
    }

    @Data
    public static class Id implements Serializable {

        private static final long serialVersionUID = 4078820027811154183L;

        private long owner;
        private long spender;
        private long tokenId;
    }
}
