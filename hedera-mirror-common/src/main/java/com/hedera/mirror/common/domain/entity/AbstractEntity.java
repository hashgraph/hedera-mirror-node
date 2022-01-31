package com.hedera.mirror.common.domain.entity;

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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.RangeToStringDeserializer;
import com.hedera.mirror.common.converter.RangeToStringSerializer;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.util.DomainUtils;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder
@TypeDef(
        defaultForType = Range.class,
        typeClass = PostgreSQLGuavaRangeType.class
)
@TypeDef(
        name = "pgsql_enum",
        typeClass = PostgreSQLEnumType.class
)
@Upsertable(history = true)
public abstract class AbstractEntity implements History {

    private Long autoRenewPeriod;

    @Column(updatable = false)
    private Long createdTimestamp;

    private Boolean deleted;

    private Long expirationTimestamp;

    @Id
    private Long id;

    @ToString.Exclude
    private byte[] key;

    private String memo;

    @Column(updatable = false)
    private Long num;

    @Convert(converter = AccountIdConverter.class)
    private EntityId proxyAccountId;

    private String publicKey;

    @Column(updatable = false)
    private Long realm;

    @Column(updatable = false)
    private Long shard;

    @Column(updatable = false)
    @Enumerated(EnumType.STRING)
    @Type(type = "pgsql_enum")
    private EntityType type;

    @JsonDeserialize(using = RangeToStringDeserializer.class)
    @JsonSerialize(using = RangeToStringSerializer.class)
    private Range<Long> timestampRange;

    public void setKey(byte[] key) {
        this.key = key;
        publicKey = DomainUtils.getPublicKey(key);
    }

    public void setMemo(String memo) {
        this.memo = DomainUtils.sanitize(memo);
    }

    public EntityId toEntityId() {
        return new EntityId(shard, realm, num, type);
    }

    // Necessary since Lombok doesn't use our setters for builders
    public abstract static class AbstractEntityBuilder<C, B extends AbstractEntityBuilder> {
        public B key(byte[] key) {
            this.key = key;
            this.publicKey = DomainUtils.getPublicKey(key);
            return (B) this;
        }

        public B memo(String memo) {
            this.memo = DomainUtils.sanitize(memo);
            return (B) this;
        }
    }
}
