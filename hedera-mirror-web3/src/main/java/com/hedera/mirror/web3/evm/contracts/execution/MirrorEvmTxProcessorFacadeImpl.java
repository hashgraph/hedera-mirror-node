package com.hedera.mirror.web3.evm.contracts.execution;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Provider;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.operation.OperationRegistry;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.StaticBlockMetaSource;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.services.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.services.evm.contracts.execution.traceability.DefaultHederaTracer;
import com.hedera.services.evm.store.contracts.AbstractCodeCache;
import com.hedera.services.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.services.evm.store.contracts.HederaEvmWorldState;
import com.hedera.services.evm.store.models.HederaEvmAccount;

@Named
public class MirrorEvmTxProcessorFacadeImpl implements MirrorEvmTxProcessorFacade {
    // Beans
    private final MirrorEntityAccess entityAccess;
    private final MirrorNodeEvmProperties evmProperties;
    private final StaticBlockMetaSource blockMetaSource;
    private final MirrorEvmContractAliases aliasManager;
    private final PricesAndFeesImpl pricesAndFees;
    // POJO
    private AbstractCodeCache codeCache;
    private HederaEvmMutableWorldState worldState;
    //HARD CODED
    private GasCalculator gasCalculator;
    private Map<String, Provider<MessageCallProcessor>> mcps;
    private Map<String, Provider<ContractCreationProcessor>> ccps;

    private MirrorEvmTxProcessor processor;

    public MirrorEvmTxProcessorFacadeImpl(MirrorEntityAccess entityAccess, MirrorNodeEvmProperties evmProperties,
                                          StaticBlockMetaSource blockMetaSource, MirrorEvmContractAliases aliasManager,
                                          PricesAndFeesImpl pricesAndFees) {
        this.aliasManager = aliasManager;
        //needed for HederaEvmWorldState
        this.entityAccess = entityAccess;
        this.evmProperties = evmProperties;
        this.codeCache = new AbstractCodeCache(1, entityAccess);
        this.blockMetaSource = blockMetaSource;
        this.pricesAndFees = pricesAndFees;
        this.worldState = new HederaEvmWorldState(entityAccess, evmProperties, codeCache);
        //
        this.gasCalculator = new BerlinGasCalculator();
        constructDummyPrecompileMaps();

        processor = new MirrorEvmTxProcessor(worldState, pricesAndFees,
                evmProperties, gasCalculator, mcps, ccps,
                blockMetaSource, aliasManager, entityAccess);
        processor.setOperationTracer(new DefaultHederaTracer());
    }

    @Override
    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData) {

        return processor.execute(sender, receiver,
                providedGasLimit, value, callData,
                Instant.now(), true);
    }

    private void constructDummyPrecompileMaps() {
        String EVM_VERSION_0_30 = "v0.30";
        String EVM_VERSION_0_32 = "v0.32";
        var evm = new EVM(new OperationRegistry(), gasCalculator, EvmConfiguration.DEFAULT);
        this.mcps =
                Map.of(
                        EVM_VERSION_0_30,
                        () -> new MessageCallProcessor(
                                evm, new PrecompileContractRegistry()),
                        EVM_VERSION_0_32,
                        () -> new MessageCallProcessor(
                                evm, new PrecompileContractRegistry()));
        this.ccps =
                Map.of(
                        EVM_VERSION_0_30,
                        () -> new ContractCreationProcessor(
                                gasCalculator, evm, true, List.of(), 1),
                        EVM_VERSION_0_32,
                        () -> new ContractCreationProcessor(
                                gasCalculator, evm, true, List.of(), 1));
    }
}
