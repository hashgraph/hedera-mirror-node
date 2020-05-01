package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_transactions")
@ToString(exclude = {"memo", "transactionHash", "transactionBytes"})
public class Transaction implements Persistable<Long> {

    @Id
    private Long consensusNs;

    @Column(name = "fk_node_acc_id")
    private Long nodeAccountId;

    private byte[] memo;

    private Integer type;

    private Integer result;

    @Column(name = "fk_payer_acc_id")
    private Long payerAccountId;

    private Long chargedTxFee;

    private Long initialBalance;

    @JoinColumn(name = "fk_cud_entity_id")
    @ManyToOne
    private Entities entity;

    private Long validStartNs;

    private Long validDurationSeconds;

    private Long maxFee;

    private byte[] transactionHash;

    private byte[] transactionBytes;

    // Helper to avoid having to update a 100 places in tests
    public Long getEntityId() {
        return entity != null ? entity.getId() : null;
    }

    @Override
    public Long getId() {
        return getConsensusNs();
    }

    @Override
    public boolean isNew() {
        return true; // Since we never update transactions and use a natural ID, avoid Hibernate querying before insert
    }
}
