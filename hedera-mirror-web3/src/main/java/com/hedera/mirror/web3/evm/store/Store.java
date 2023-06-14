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

package com.hedera.mirror.web3.evm.store;

import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import org.hyperledger.besu.datatypes.Address;

public interface Store {

    Account getAccount(Address address, boolean throwIfMissing);

    Token getToken(Address address, boolean throwIfMissing);

    TokenRelationship getTokenRelationship(TokenRelationshipKey tokenRelationshipKey, boolean throwIfMissing);

    UniqueToken getUniqueToken(NftId nftId, boolean throwIfMissing);

    void updateAccount(Account updatedAccount);

    void updateTokenRelationship(TokenRelationship updatedTokenRelationship);

    void addPendingChanges();

    void commit();

    void wrap();
}
