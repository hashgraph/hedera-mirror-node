package com.hedera.mirror.web3.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

import lombok.Builder;
import lombok.Data;

import com.hedera.mirror.web3.config.validation.Address;

import lombok.Value;

@Data
public class ContractCallRequest {

    BlockType block = BlockType.LATEST;

    String data;

    @Address
    String from;

    @Min(0)
    long gas;

    @Min(0)
    long gasPrice;

    @Address
    @NotEmpty
    String to;

    @Min(0)
    long value;

    boolean estimate;
}
