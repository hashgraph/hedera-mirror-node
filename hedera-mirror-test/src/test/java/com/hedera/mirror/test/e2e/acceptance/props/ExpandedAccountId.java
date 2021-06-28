package com.hedera.mirror.test.e2e.acceptance.props;

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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;

@Data
@AllArgsConstructor
public class ExpandedAccountId {
    private final AccountId accountId;
    @ToString.Exclude
    private final PrivateKey privateKey;
    @ToString.Exclude
    private final PublicKey publicKey;

    public ExpandedAccountId(String operatorId, String operatorKey) {
        accountId = AccountId.fromString(operatorId);
        privateKey = PrivateKey.fromString(operatorKey);
        publicKey = privateKey.getPublicKey();
    }
}
