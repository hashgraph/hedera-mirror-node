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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import javax.persistence.Convert;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.TypeDef;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.RangeToStringDeserializer;
import com.hedera.mirror.importer.converter.RangeToStringSerializer;
import com.hedera.mirror.importer.util.Utility;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder
@TypeDef(
        defaultForType = Range.class,
        typeClass = PostgreSQLGuavaRangeType.class
)
public abstract class AbstractEntity {

    private Long autoRenewPeriod;

    private Long createdTimestamp;

    private Boolean deleted;

    private Long expirationTimestamp;

    @Id
    private Long id;

    @ToString.Exclude
    private byte[] key;

    private String memo;

    private Long num;

    @Convert(converter = AccountIdConverter.class)
    private EntityId proxyAccountId;

    private String publicKey;

    private Long realm;

    private Long shard;

    private Integer type;

    @JsonDeserialize(using = RangeToStringDeserializer.class)
    @JsonSerialize(using = RangeToStringSerializer.class)
    private Range<Long> timestampRange;

    @JsonIgnore
    public Long getModifiedTimestamp() {
        return timestampRange != null ? timestampRange.lowerEndpoint() : null;
    }

    public void setModifiedTimestamp(long modifiedTimestamp) {
        timestampRange = Range.atLeast(modifiedTimestamp);
    }

    public void setKey(byte[] key) {
        this.key = key;
        publicKey = Utility.convertSimpleKeyToHex(key);
    }

    public void setMemo(String memo) {
        this.memo = Utility.sanitize(memo);
    }

    public EntityId toEntityId() {
        return new EntityId(shard, realm, num, type);
    }

    // Necessary since Lombok doesn't use our setters for builders
    public abstract static class AbstractEntityBuilder<C, B extends AbstractEntityBuilder> {
        public B key(byte[] key) {
            this.key = key;
            this.publicKey = Utility.convertSimpleKeyToHex(key);
            return (B) this;
        }

        public B memo(String memo) {
            this.memo = Utility.sanitize(memo);
            return (B) this;
        }
    }
}
