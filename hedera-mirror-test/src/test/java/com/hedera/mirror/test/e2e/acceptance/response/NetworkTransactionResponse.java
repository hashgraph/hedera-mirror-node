package com.hedera.mirror.test.e2e.acceptance.response;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;

@Data
@Log4j2
public class NetworkTransactionResponse {
    private final TransactionId transactionId;
    private final TransactionReceipt receipt;

    public String getTransactionIdString() {
        return transactionId.accountId.toString() + "-" + transactionId.validStart
                .getEpochSecond() + "-" + getPaddedNanos();
    }

    public String getValidStartString() {

        // left pad nanos with zeros where applicable
        return transactionId.validStart.getEpochSecond() + "." + getPaddedNanos();
    }

    private String getPaddedNanos() {
        String nanos = String.valueOf(transactionId.validStart.getNano());
        return StringUtils.leftPad(nanos, 9, '0');
    }
}
