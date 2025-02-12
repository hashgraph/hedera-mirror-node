/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
final class UtilPrngTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {

        if (!blockItem.successful()) {
            return;
        }

        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasUtilPrng()) {
                var utilPrng = transactionOutput.getUtilPrng();
                switch (utilPrng.getEntropyCase()) {
                    case PRNG_NUMBER -> transactionRecordBuilder.setPrngNumber(utilPrng.getPrngNumber());
                    case PRNG_BYTES -> transactionRecordBuilder.setPrngBytes(utilPrng.getPrngBytes());
                    default -> UtilPrngTransformer.log.warn("Unhandled entropy case: {}", utilPrng.getEntropyCase());
                }
                return;
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.UTILPRNG;
    }
}
