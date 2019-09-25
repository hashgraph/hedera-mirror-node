package com.hedera.mirror.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;

@Data
@Entity
@Table(name = "t_entities")
public class Entities {

    @Id
    @Getter @Setter private Long id;

    @Column(name = "entity_num")
    @Getter @Setter private Long entityNum;

    @Column(name = "entity_realm")
    @Getter @Setter private Long entityRealm;

    @Column(name = "entity_shard")
    @Getter @Setter private Long entityShard;

    @Column(name = "fk_entity_type_id")
    @Getter @Setter private Integer entityTypeId;
    
    @Column(nullable=true, name = "exp_time_seconds")
    @Getter @Setter private Long expiryTimeSeconds;
    
    @Column(nullable=true, name = "exp_time_nanos")
    @Getter @Setter private Long expiryTimeNanos;

    @Column(nullable=true, name = "auto_renew_period")
    @Getter @Setter private Long autoRenewPeriod;

    @Column(nullable=true, name = "key")
    @Getter @Setter private byte[] key;

    @Column(nullable=true, name = "fk_prox_acc_id")
    @Getter @Setter private Long proxyAccountId;
    
    @Column(name = "deleted")
    @Getter @Setter private boolean deleted;

    @Column(nullable=true, name = "exp_time_ns")
    @Getter @Setter private Long expiryTimeNs;

    @Column(nullable=true, name = "ed25519_public_key_hex")
    @Getter @Setter private String ed25519PublicKeyHex;
}
