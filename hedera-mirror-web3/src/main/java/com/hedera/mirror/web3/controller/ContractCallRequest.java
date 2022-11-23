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
import javax.validation.constraints.NotNull;
import lombok.Data;

import com.hedera.mirror.web3.controller.validation.Address;

@Data
public class ContractCallRequest {

    @NotNull
    private BlockType block = BlockType.LATEST;

    private String data;

    private boolean estimate;

    @Address
    private String from;

    @Min(0)
    private long gas;

    @Min(0)
    private long gasPrice;

    @Address
    @NotEmpty
    private String to;

    @Min(0)
    private long value;
}
