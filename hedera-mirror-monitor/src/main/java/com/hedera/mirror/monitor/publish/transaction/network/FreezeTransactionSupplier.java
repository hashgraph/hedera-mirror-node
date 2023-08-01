/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.publish.transaction.network;

import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.FreezeTransaction;
import com.hedera.hashgraph.sdk.FreezeType;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import java.time.Instant;

public class FreezeTransactionSupplier implements TransactionSupplier<FreezeTransaction> {

    private byte[] fileHash;
    private String fileId;
    private FreezeType freezeType = FreezeType.FREEZE_ONLY;
    private Instant startTime;

    @Override
    public Transaction<FreezeTransaction> get() {
        return new FreezeTransaction()
                .setFreezeType(freezeType)
                .setFileId(FileId.fromString(fileId))
                .setFileHash(fileHash)
                .setStartTime(startTime);
    }
}
