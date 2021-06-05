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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import javax.persistence.Convert;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.EntityIdSerializer;
import com.hedera.mirror.importer.converter.NullableStringSerializer;
import com.hedera.mirror.importer.util.Utility;

@Data
@javax.persistence.Entity
@Log4j2
@ToString(exclude = {"key", "submitKey"})
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@AllArgsConstructor
public class Entity {
    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId autoRenewAccountId;

    private Long autoRenewPeriod;

    private Long createdTimestamp;

    private Boolean deleted;

    private Long expirationTimestamp;

    @Id
    private Long id;

    private byte[] key;

    @JsonSerialize(using = NullableStringSerializer.class)
    private String memo;

    private Long modifiedTimestamp;

    private Long num;

    @JsonSerialize(using = NullableStringSerializer.class)
    private String publicKey; // null as default, "" Key.getDefaultInstance() case.

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId proxyAccountId;

    private Long realm;

    private Long shard;

    private byte[] submitKey;

    private Integer type;

    public Entity() {
    }

    public void setKey(byte[] key) {
        this.key = key;
        publicKey = Utility.convertSimpleKeyToHex(key);
    }

    public void setMemo(String memo) {
        this.memo = StringUtils.isEmpty(memo) ? "" : Utility.sanitize(memo);
    }

    public EntityId toEntityId() {
        return new EntityId(shard, realm, num, type);
    }
}
