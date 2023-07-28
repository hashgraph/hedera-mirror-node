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

package com.hedera.services.fees.calculation.crypto.queries;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.hapi.utils.fees.CryptoFeeBuilder;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Removed child record logic
 *  2. Removed retching TransactionRecord from AnswerFunctions, since we do not save records in TxnIdRecentHistory
 */
public class GetTxnRecordResourceUsage implements QueryResourceUsageEstimator {
    static final TransactionRecord MISSING_RECORD_STANDIN = TransactionRecord.getDefaultInstance();
    private final CryptoFeeBuilder usageEstimator;

    public GetTxnRecordResourceUsage(CryptoFeeBuilder usageEstimator) {
        this.usageEstimator = usageEstimator;
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasTransactionGetRecord();
    }

    @Override
    public FeeData usageGiven(Query query, Store store, @Nullable Map<String, Object> queryCtx) {
        return usageFor(query.getTransactionGetRecord().getHeader().getResponseType());
    }

    // removed child records logic
    private FeeData usageFor(final ResponseType stateProofType) {
        return usageEstimator.getTransactionRecordQueryFeeMatrices(MISSING_RECORD_STANDIN, stateProofType);
    }
}
