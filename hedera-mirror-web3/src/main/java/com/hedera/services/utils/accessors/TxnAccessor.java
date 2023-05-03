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

package com.hedera.services.utils.accessors;

import com.hedera.services.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;

/**
 * Defines a type that gives access to several commonly referenced parts of a Hedera Services gRPC {@link Transaction}.
 */
public interface TxnAccessor {
    long getOfferedFee();

    SubType getSubType();

    TransactionID getTxnId();

    BaseTransactionMeta baseUsageMeta();

    SigUsage usageGiven(int numPayerKeys);

    TransactionBody getTxn();

    HederaFunctionality getFunction();

    String getMemo();

    byte[] getHash();
}
