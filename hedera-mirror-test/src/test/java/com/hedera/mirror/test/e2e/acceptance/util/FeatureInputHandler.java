package com.hedera.mirror.test.e2e.acceptance.util;

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

import java.time.DateTimeException;
import java.time.Instant;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractId;

public class FeatureInputHandler {
    public static Instant messageQueryDateStringToInstant(String date) {
        return messageQueryDateStringToInstant(date, Instant.now());
    }

    public static Instant messageQueryDateStringToInstant(String date, Instant referenceInstant) {
        Instant refDate;
        try {
            refDate = Instant.parse(date);
        } catch (DateTimeException dtex) {
            refDate = referenceInstant.plusSeconds(Long.parseLong(date));
        }

        return refDate;
    }

    public static String solidityAddress(AccountId accountId) {
        return FeatureInputHandler.solidityAddress(accountId.shard, accountId.realm, accountId.num);
    }

    public static String solidityAddress(ContractId contractId) {
        return FeatureInputHandler.solidityAddress(contractId.shard, contractId.realm, contractId.num);
    }

    public static String solidityAddress(long shard, long realm, long num) {
        return String.format("0x%08x%016x%016x", shard, realm, num);
    }
}
