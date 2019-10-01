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
import javax.persistence.*;

@Data
@Entity
@Table(name = "t_entities")
public class Entities {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long entityNum;

    private Long entityRealm;

    private Long entityShard;

    @Column(name = "fk_entity_type_id")
    private Integer entityTypeId;
    
    @Column(name = "exp_time_seconds")
    private Long expiryTimeSeconds;
    
    @Column(name = "exp_time_nanos")
    private Long expiryTimeNanos;

    private Long autoRenewPeriod;

    private byte[] key;

    @Column(name = "fk_prox_acc_id")
    private Long proxyAccountId;
    
    private boolean deleted;

    @Column(name = "exp_time_ns")
    private Long expiryTimeNs;

    @Column(name = "ed25519_public_key_hex")
    private String ed25519PublicKeyHex;
}
