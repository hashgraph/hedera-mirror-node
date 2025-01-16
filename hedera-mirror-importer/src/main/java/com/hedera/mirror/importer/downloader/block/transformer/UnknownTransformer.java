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

import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionRecord.Builder;
import jakarta.inject.Named;
import java.util.List;

@Named
public class UnknownTransformer extends AbstractBlockItemTransformer {

    @Override
    public TransactionType getType() {
        return TransactionType.UNKNOWN;
    }

    @Override
    protected void updateTransactionRecord(
            List<TransactionOutput> transactionOutputs, Builder transactionRecordBuilder) {
        // Unknown transaction type, no known actions
    }
}
