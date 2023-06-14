/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.crypto;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.jproto.JKey;
import com.hedera.services.ledger.ids.EntityIdSource;
import org.hyperledger.besu.datatypes.Address;

/**
 * Responsible for creating accounts during a crypto transfer that sends hbar to a previously unused alias.
 *
 * Copied Logic type from hedera-services. Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 */
public class AutoCreationLogic extends AbstractAutoCreationLogic {

    public AutoCreationLogic(
            final EntityIdSource ids, final Store store, MirrorEvmContractAliases mirrorEvmContractAliases) {
        super(ids, store, mirrorEvmContractAliases);
    }

    @Override
    protected void trackAlias(final JKey jKey, final Address alias) {
        mirrorEvmContractAliases.maybeLinkEvmAddress(jKey, alias);
    }
}
