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

package com.hedera.mirror.importer.parser.contractresult;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;

@Data
@RequiredArgsConstructor
public abstract class AbstractSyntheticContractResult implements SyntheticContractResult {
    private final RecordItem recordItem;
    private final EntityId entityId;
    private final EntityId senderId;
    private final byte[] functionParameters;

    static final byte[] TRANSFER_SIGNATURE = hexToBytes("a9059cbb");

    static final byte[] APPROVE_SIGNATURE = hexToBytes("095ea7b3");

    static byte[] hexToBytes(String hex) {
        return Bytes.fromHexString(hex).toArrayUnsafe();
    }
}
