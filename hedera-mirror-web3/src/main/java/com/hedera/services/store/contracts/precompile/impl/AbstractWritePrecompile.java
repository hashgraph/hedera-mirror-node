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

package com.hedera.services.store.contracts.precompile.impl;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

/**
 * This class is a modified copy of AllowancePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Added util method to unalias given address
 */
public abstract class AbstractWritePrecompile implements Precompile {
    protected static final String FAILURE_MESSAGE = "Invalid full prefix for %s precompile!";
    protected final PrecompilePricingUtils pricingUtils;
    protected final SyntheticTxnFactory syntheticTxnFactory;

    protected AbstractWritePrecompile(
            final PrecompilePricingUtils pricingUtils, final SyntheticTxnFactory syntheticTxnFactory) {
        this.pricingUtils = pricingUtils;
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    @Override
    public long getGasRequirement(
            long blockTimestamp,
            final TransactionBody.Builder transactionBody,
            final Store store,
            final HederaEvmContractAliases mirrorEvmContractAliases,
            final Address senderAddress) {
        return pricingUtils.computeGasRequirement(
                blockTimestamp, this, transactionBody, store, mirrorEvmContractAliases, senderAddress);
    }

    protected Address unalias(Address addressOrAlias, HederaEvmStackedWorldStateUpdater updater) {
        return Address.wrap(Bytes.wrap(updater.permissivelyUnaliased(addressOrAlias.toArray())));
    }
}
