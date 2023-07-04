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

import static com.hedera.services.utils.EntityIdUtils.isAliasSizeGreaterThanEvmAddress;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asPrimitiveKeyUnchecked;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * Responsible for creating accounts during a crypto transfer that sends hbar to a previously unused alias.
 *
 * Copied Logic type from hedera-services. Differences with the original:
 * 1. Use abstraction for the state by introducing {@link Store} interface
 * 2. Remove unused methods: reclaimPendingAliases, trackSigImpactIfNeeded, getPendingCreations, getTokenAliasMap
 * 3. The class is stateless and the arguments are passed into the functions
 */
public class AutoCreationLogic extends AbstractAutoCreationLogic {

    public AutoCreationLogic(
            final FeeCalculator feeCalculator,
            final EvmProperties evmProperties,
            final SyntheticTxnFactory syntheticTxnFactory) {
        super(feeCalculator, evmProperties, syntheticTxnFactory);
    }

    @Override
    protected void trackAlias(
            final ByteString alias, final Address address, final MirrorEvmContractAliases mirrorEvmContractAliases) {
        if (isAliasSizeGreaterThanEvmAddress(alias)) {
            // if the alias is not derived from ECDSA public key
            final var key = asPrimitiveKeyUnchecked(alias);
            JKey jKey = asFcKeyUnchecked(key);
            mirrorEvmContractAliases.maybeLinkEvmAddress(jKey, address);

        } else {
            // if the alias is derived from ECDSA public key
            mirrorEvmContractAliases.link(Address.wrap(Bytes.wrap(alias.toByteArray())), address);
        }
    }
}
