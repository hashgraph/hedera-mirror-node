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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Data
@Entity
@Table(name = "t_transactions")
public class Transaction {

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

    @Column(name = "fk_cud_entity_id")
    private Long entityId;

    @Column(name = "fk_rec_file_id")
    private Long recordFileId;

    private Long validStartNs;

    private Long validDurationSeconds;

    private Long maxFee;

    private byte[] transactionHash;

    private byte[] transactionBytes;
}
