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
@Table(name = "t_transactions")
public class Transaction {

    @Id
    @Column(name = "consensus_ns")
    @Getter @Setter private Long consensusNs;

    @Column(name = "fk_node_acc_id")
    @Getter @Setter private Long nodeAccountId;

    @Column(nullable=true, name="memo")
    @Getter @Setter private byte[] memo;

    @Column(name = "fk_trans_type_id")
    @Getter @Setter  private Long transactionTypeId;

    @Column(name = "fk_result_id")
    @Getter @Setter private Integer resultId;

    @Column(name = "fk_payer_acc_id")
    @Getter @Setter private Long payerAccountId;

    @Column(name = "charged_tx_fee")
    @Getter @Setter private Long chargedTxFee;

    @Column(name = "initial_balance")
    @Getter @Setter private Long initialBalance;

    @Column(nullable=true, name = "fk_cud_entity_id")
    @Getter @Setter private Long entityId;

    @Column(name = "fk_rec_file_id")
    @Getter @Setter private Long recordFileId;

    @Column(name = "valid_start_ns")
    @Getter @Setter private Long validStartNs;

}