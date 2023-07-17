/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.web3.viewmodel;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hedera.mirror.web3.convert.BlockTypeDeserializer;
import com.hedera.mirror.web3.convert.BlockTypeSerializer;
import com.hedera.mirror.web3.validation.Hex;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.hyperledger.besu.datatypes.Address;

@Data
public class ContractCallRequest {

    public static final int ADDRESS_LENGTH = 40;

    @JsonSerialize(using = BlockTypeSerializer.class)
    @JsonDeserialize(using = BlockTypeDeserializer.class)
    private BlockType block = BlockType.LATEST;

    @Hex(maxLength = 24576 * 2) // HAPI caps contract creates at 24KiB
    private String data;

    private boolean estimate;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH)
    private String from;

    @Min(21_000)
    @Max(15_000_000)
    private long gas = 15_000_000L;

    @Min(0)
    private long gasPrice;

    @Hex(minLength = ADDRESS_LENGTH, maxLength = ADDRESS_LENGTH, allowEmpty = true)
    private String to;

    @PositiveOrZero
    private long value;

    @AssertTrue(message = "must not be empty")
    private boolean hasFrom() {
        return value <= 0 || from != null;
    }

    @AssertTrue(message = "must not be empty")
    private boolean hasTo() {
        final var isBlankOrEmpty = to == null || to.isEmpty();
        if (!estimate && isBlankOrEmpty) {
            return false;
        }
        /*When performing estimateGas with an empty "to" field, we set a default value of the zero address
        to avoid any potential NullPointerExceptions throughout the process.*/
        if (isBlankOrEmpty) {
            to = Address.ZERO.toHexString();
        }
        return true;
    }

    @AssertTrue(message = "must not exceed call size limit")
    private boolean hasData() {
        if (data == null) {
            return true;
        } else {
            final var dataSize = data.length();

            // In case of contract calls we should limit requests to 6KiB
            return to == null || dataSize <= 6144 * 2;
        }
    }
}
