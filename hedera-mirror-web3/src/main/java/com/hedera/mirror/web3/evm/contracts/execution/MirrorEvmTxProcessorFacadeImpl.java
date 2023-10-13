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
import com.hedera.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import com.hedera.mirror.web3.evm.contracts.operations.HederaPrngSeedOperation;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.StaticBlockMetaSource;
import com.hedera.mirror.web3.evm.properties.TraceProperties;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmWorldState;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.evm.token.TokenAccessorImpl;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.repository.ContractStateRepository;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hedera.services.store.models.Account;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Named
@SuppressWarnings("java:S107")
public class MirrorEvmTxProcessorFacadeImpl implements MirrorEvmTxProcessorFacade {

    private final AbstractAutoCreationLogic autoCreationLogic;
    private final MirrorNodeEvmProperties evmProperties;
    private final StaticBlockMetaSource blockMetaSource;
    private final LivePricesSource pricesAndFees;
    private final GasCalculatorHederaV22 gasCalculator;
    private final PrecompileMapper precompileMapper;
    private final EntityAddressSequencer entityAddressSequencer;
    private final List<DatabaseAccessor<Object, ?>> databaseAccessors;
    private final ContractRepository contractRepository;
    private final ContractStateRepository contractStateRepository;
    private final TraceProperties traceProperties;
    private final BasicHbarCentExchange basicHbarCentExchange;
    private final PrngSystemPrecompiledContract prngSystemPrecompiledContract;
    private final HederaPrngSeedOperation prngSeedOperation;

    @SuppressWarnings("java:S107")
    public MirrorEvmTxProcessorFacadeImpl(
            final AbstractAutoCreationLogic autoCreationLogic,
            final MirrorNodeEvmProperties evmProperties,
            final TraceProperties traceProperties,
            final StaticBlockMetaSource blockMetaSource,
            final LivePricesSource pricesAndFees,
            final GasCalculatorHederaV22 gasCalculator,
            final EntityAddressSequencer entityAddressSequencer,
            final ContractRepository contractRepository,
            final ContractStateRepository contractStateRepository,
            final List<DatabaseAccessor<Object, ?>> databaseAccessors,
            final PrecompileMapper precompileMapper,
            final BasicHbarCentExchange basicHbarCentExchange,
            final PrngSystemPrecompiledContract prngSystemPrecompiledContract,
            final HederaPrngSeedOperation prngSeedOperation) {
        this.evmProperties = evmProperties;
        this.blockMetaSource = blockMetaSource;
        this.traceProperties = traceProperties;
        this.pricesAndFees = pricesAndFees;
        this.gasCalculator = gasCalculator;
        this.autoCreationLogic = autoCreationLogic;
        this.precompileMapper = precompileMapper;
        this.entityAddressSequencer = entityAddressSequencer;
        this.contractRepository = contractRepository;
        this.contractStateRepository = contractStateRepository;
        this.databaseAccessors = databaseAccessors;
        this.basicHbarCentExchange = basicHbarCentExchange;
        this.prngSystemPrecompiledContract = prngSystemPrecompiledContract;
        this.prngSeedOperation = prngSeedOperation;
    }

    @Override
    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final Instant consensusTimestamp,
            final boolean isStatic,
            final boolean isEstimate) {
        final int expirationCacheTime =
                (int) evmProperties.getExpirationCacheTime().toSeconds();
        final var store = new StoreImpl(databaseAccessors);
        final var mirrorEvmContractAliases = new MirrorEvmContractAliases(store);
        final var mirrorEntityAccess = new MirrorEntityAccess(contractStateRepository, contractRepository, store);
        final var tokenAccessor = new TokenAccessorImpl(evmProperties, store, mirrorEvmContractAliases);
        final var accountAccessor = new AccountAccessorImpl(store, mirrorEntityAccess, mirrorEvmContractAliases);
        final var codeCache = new AbstractCodeCache(expirationCacheTime, mirrorEntityAccess);
        final var mirrorOperationTracer = new MirrorOperationTracer(traceProperties, mirrorEvmContractAliases);

        store.wrap();
        final var worldState = new HederaEvmWorldState(
                mirrorEntityAccess,
                evmProperties,
                codeCache,
                accountAccessor,
                tokenAccessor,
                entityAddressSequencer,
                mirrorEvmContractAliases,
                store);

        final var processor = new MirrorEvmTxProcessor(
                worldState,
                pricesAndFees,
                evmProperties,
                gasCalculator,
                mcps(
                        gasCalculator,
                        autoCreationLogic,
                        entityAddressSequencer,
                        mirrorEvmContractAliases,
                        evmProperties,
                        precompileMapper,
                        basicHbarCentExchange,
                        prngSystemPrecompiledContract,
                        prngSeedOperation,
                        isEstimate),
                ccps(gasCalculator, evmProperties, prngSeedOperation),
                blockMetaSource,
                mirrorEvmContractAliases,
                codeCache,
                Address.ZERO.equals(receiver));

        processor.setOperationTracer(mirrorOperationTracer);

        // In case of eth_estimateGas we add a default account with zero address to cover cases with missing sender
        if (isEstimate) {
            final var defaultAccount = Account.getDefaultAccount();
            store.updateAccount(defaultAccount);
        }
        return processor.execute(sender, receiver, providedGasLimit, value, callData, consensusTimestamp, isStatic);
    }
}
