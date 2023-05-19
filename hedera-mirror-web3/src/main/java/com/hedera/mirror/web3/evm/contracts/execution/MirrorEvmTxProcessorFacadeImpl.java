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

package com.hedera.mirror.web3.evm.contracts.execution;

import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.ccps;
import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.mcps;

import com.hedera.mirror.web3.evm.account.AccountAccessorImpl;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.StaticBlockMetaSource;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.accessor.*;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmWorldState;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.evm.token.TokenAccessorImpl;
import com.hedera.mirror.web3.repository.*;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.execution.traceability.DefaultHederaTracer;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import java.time.Instant;
import java.util.List;
import javax.inject.Named;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Named
@SuppressWarnings("java:S107")
public class MirrorEvmTxProcessorFacadeImpl implements MirrorEvmTxProcessorFacade {

    private final MirrorNodeEvmProperties evmProperties;
    private final StaticBlockMetaSource blockMetaSource;
    private final MirrorEvmContractAliases mirrorEvmContractAliases;
    private final PricesAndFeesImpl pricesAndFees;
    private final AbstractCodeCache codeCache;
    private final HederaEvmMutableWorldState worldState;
    private final GasCalculatorHederaV22 gasCalculator;
    private final List<DatabaseAccessor<Object,?>> databaseAccessors;

    public MirrorEvmTxProcessorFacadeImpl(
            final MirrorEntityAccess entityAccess,
            final MirrorNodeEvmProperties evmProperties,
            final StaticBlockMetaSource blockMetaSource,
            final MirrorEvmContractAliases mirrorEvmContractAliases,
            final PricesAndFeesImpl pricesAndFees,
            final AccountAccessorImpl accountAccessor,
            final TokenAccessorImpl tokenAccessor,
            final GasCalculatorHederaV22 gasCalculator,
            final EntityAddressSequencer entityAddressSequencer,
            final List<DatabaseAccessor<Object,?>> databaseAccessors) {
        this.evmProperties = evmProperties;
        this.blockMetaSource = blockMetaSource;
        this.mirrorEvmContractAliases = mirrorEvmContractAliases;
        this.pricesAndFees = pricesAndFees;
        this.gasCalculator = gasCalculator;
        this.databaseAccessors = databaseAccessors;

        final int expirationCacheTime =
                (int) evmProperties.getExpirationCacheTime().toSeconds();

        this.codeCache = new AbstractCodeCache(expirationCacheTime, entityAccess);

        this.worldState = new HederaEvmWorldState(
                entityAccess, evmProperties, codeCache, accountAccessor, tokenAccessor, entityAddressSequencer, mirrorEvmContractAliases);
    }

    @Override
    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final boolean isStatic) {
        final var stackedStateFrames = new StackedStateFrames<>(databaseAccessors);

        final var processor = new MirrorEvmTxProcessor(
                worldState,
                pricesAndFees,
                evmProperties,
                gasCalculator,
                mcps(gasCalculator, stackedStateFrames),
                ccps(gasCalculator),
                blockMetaSource,
                mirrorEvmContractAliases,
                codeCache);

        processor.setOperationTracer(new DefaultHederaTracer());

        return processor.execute(sender, receiver, providedGasLimit, value, callData, Instant.now(), isStatic);
    }
}
