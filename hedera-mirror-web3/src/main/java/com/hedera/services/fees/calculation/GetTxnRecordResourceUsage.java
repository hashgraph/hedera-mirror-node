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

package com.hedera.services.fees.calculation;

import com.hedera.mirror.web3.evm.store.Store;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public class GetTxnRecordResourceUsage implements QueryResourceUsageEstimator {
    @Override
    public boolean applicableTo(final Query query) {
        return false;
    }

    @Override
    public FeeData usageGiven(Query query, Store store, @Nullable Map<String, Object> queryCtx) {
        return null;
    }
}
