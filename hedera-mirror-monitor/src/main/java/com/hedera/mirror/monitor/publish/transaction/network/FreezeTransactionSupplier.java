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
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class FreezeTransactionSupplier implements TransactionSupplier<FreezeTransaction> {

    private byte[] fileHash;

    private String fileId;

    @NotNull
    private FreezeType freezeType = FreezeType.FREEZE_ONLY;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotNull
    private Instant startTime = Instant.now();

    @Override
    public FreezeTransaction get() {
        var freezeTransaction = new FreezeTransaction()
                .setFreezeType(freezeType)
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setStartTime(startTime);

        if (fileHash != null) {
            freezeTransaction.setFileHash(fileHash);
        }

        if (StringUtils.isNotBlank(fileId)) {
            freezeTransaction.setFileId(FileId.fromString(fileId));
        }

        return freezeTransaction;
    }
}
