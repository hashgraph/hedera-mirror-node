package com.hedera.mirror.web3.viewmodel;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.hibernate.validator.group.GroupSequenceProvider;

import com.hedera.mirror.web3.convert.BlockTypeDeserializer;
import com.hedera.mirror.web3.convert.BlockTypeSerializer;
import com.hedera.mirror.web3.validation.Hex;

@Data
@GroupSequenceProvider(TransferValidation.class)
public class ContractCallRequest {

    private static final int ADDRESS_LENGTH = 40;

    @JsonSerialize(using = BlockTypeSerializer.class)
    @JsonDeserialize(using = BlockTypeDeserializer.class)
    private BlockType block = BlockType.LATEST;

    @Hex(maxLength = 6144 * 2) // HAPI caps request at 6KiB
    private String data;

    private boolean estimate;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    @NotNull(groups = TransferCheck.class)
    private String from;

    @Min(0)
    private long gas = 15_000_000L;

    @Min(0)
    private long gasPrice;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    @NotEmpty
    private String to;

    @PositiveOrZero
    @Min(value = 1, groups = TransferCheck.class)
    private long value;
}
