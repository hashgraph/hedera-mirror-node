package com.hedera.mirror.importer.domain;

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

import com.google.protobuf.InvalidProtocolBufferException;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

import com.hedera.mirror.importer.util.Utility;

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

    private byte[] submitKey;

    private Long topicValidStartTime;

    public void setKey(byte[] key) {
        try {
            this.key = key;
            ed25519PublicKeyHex = Utility.protobufKeyToHexIfEd25519OrNull(key);
        } catch (InvalidProtocolBufferException e) {
            ed25519PublicKeyHex = null;
        }
    }

    public void setExpiryTimeNs(Long expiryTimeNs) {
        this.expiryTimeNs = expiryTimeNs;
        Instant instant = Instant.ofEpochSecond(0, expiryTimeNs);
        setExpiryTimeSeconds(instant.getEpochSecond());
        setExpiryTimeNanos((long) instant.getNano());
    }
}
