package com.hedera.mirror.importer.parser.contractlog;

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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;

import lombok.Data;
import org.apache.tuweni.bytes.Bytes;
@Data
public abstract class AbstractSyntheticContractLog implements SyntheticContractLog{
    private final byte[] data;
    private final EntityId entityId;
    private final byte[] topic0;
    private final byte[] topic1;
    private final byte[] topic2;
    private final RecordItem recordItem;

    AbstractSyntheticContractLog(byte[] data, EntityId tokenId, byte[] topic0, byte[] topic1, byte[] topic2, RecordItem recordItem) {
        this.data = data;
        this.entityId = tokenId;
        this.recordItem = recordItem;
        this.topic0 = topic0;
        this.topic1 = topic1;
        this.topic2 = topic2;
    }
    static final byte[] TRANSFER_SIGNATURE = Bytes.fromHexString("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef").toArray();
    static final byte[] APPROVE_FOR_ALL_SIGNATURE = Bytes.fromHexString("17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31").toArray();
    static final byte[] APPROVE_SIGNATURE = Bytes.fromHexString("8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925").toArray();
    static final byte[] EMPTY_FIELD = Bytes.of(0).toArray();

    static byte[] entityIdToBytes(EntityId entityId) { return Bytes.ofUnsignedLong(entityId.getEntityNum()).toArrayUnsafe(); }
    static byte[] longToBytes(long value) { return Bytes.ofUnsignedLong(value).toArrayUnsafe(); }
    static byte[] booleanToBytes(boolean value) { return Bytes.of(value ? 1 : 0).toArrayUnsafe(); }
}
