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
@Table(name = "t_contract_result")
public class ContractResult {

    @Id
    private Long fk_trans_id;

    @Column(nullable=true, name = "function_params")
    private byte[] functionParameters;

    @Column(name = "gas_supplied")
    private Long gasSupplied;

    @Column(nullable=true, name = "call_result")
    private byte[] callResult;

    @Column(name = "gas_used")
    private Long gasUsed;

}