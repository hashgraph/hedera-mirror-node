/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.ledger.ids;

import com.hederahashgraph.api.proto.java.AccountID;

/** Defines a type able to create ids of various entities under various conditions. */
public interface EntityIdSource {

    /**
     * Returns the {@link AccountID} to use for a new account
     *
     * @return an appropriate id to use
     */
    AccountID newAccountId();
}
