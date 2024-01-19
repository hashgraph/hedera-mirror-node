/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;

public class FungibleTokenTransfer extends HbarTransfer {

    private final TokenID denomination;

    public FungibleTokenTransfer(
            final long amount,
            final boolean isApproval,
            final TokenID denomination,
            final AccountID sender,
            final AccountID receiver) {
        super(amount, isApproval, sender, receiver);
        this.denomination = denomination;
    }

    public TokenID getDenomination() {
        return denomination;
    }
}
