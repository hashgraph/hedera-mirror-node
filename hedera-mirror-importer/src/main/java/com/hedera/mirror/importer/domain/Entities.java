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

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.util.Utility;

@Data
@Entity
@Log4j2
@Table(name = "t_entities")
@ToString(exclude = {"key", "submitKey"})
public class Entities {
    @Id
    private Long id;

    private Long entityNum;

    private Long entityRealm;

    private Long entityShard;

    @Column(name = "fk_entity_type_id")
    private Integer entityTypeId;

    @Convert(converter = AccountIdConverter.class)
    private EntityId autoRenewAccountId;

    private Long autoRenewPeriod;

    private byte[] key;

    @Convert(converter = AccountIdConverter.class)
    private EntityId proxyAccountId;

    private boolean deleted;

    @Column(name = "exp_time_ns")
    private Long expiryTimeNs;

    @Column(name = "ed25519_public_key_hex")
    private String ed25519PublicKeyHex;

    private byte[] submitKey;

    private String memo;

    public void setKey(byte[] key) {
        this.key = key;
        ed25519PublicKeyHex = Utility.convertSimpleKeyToHex(key);
    }

    public void setMemo(String memo) {
        this.memo = Utility.sanitize(memo);
    }

    public EntityId toEntityId() {
        return new EntityId(entityShard, entityRealm, entityNum, entityTypeId);
    }
}
