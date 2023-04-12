package com.hedera.mirror.test.e2e.acceptance.props;

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

import lombok.AllArgsConstructor;
import lombok.Value;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;

@Value
@AllArgsConstructor
public class ExpandedAccountId {

    private final AccountId accountId;
    private final PrivateKey privateKey;

    public ExpandedAccountId(String operatorId, String operatorKey) {
        this(AccountId.fromString(operatorId), PrivateKey.fromString(operatorKey));
    }

    public ExpandedAccountId(AccountId account) {
        this(account, null);
    }

    public PublicKey getPublicKey() {
        return privateKey != null ? privateKey.getPublicKey() : null;
    }

    @Override
    public String toString() {
        return accountId.toString();
    }
}
