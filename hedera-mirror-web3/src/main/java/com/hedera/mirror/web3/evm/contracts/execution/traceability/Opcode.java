/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.contracts.execution.traceability;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.apache.tuweni.bytes.Bytes;

@Builder
public record Opcode(
        int pc,
        String op,
        long gas,
        long gasCost,
        int depth,
        List<Bytes> stack,
        List<Bytes> memory,
        Map<Bytes, Bytes> storage,
        String reason) {}
