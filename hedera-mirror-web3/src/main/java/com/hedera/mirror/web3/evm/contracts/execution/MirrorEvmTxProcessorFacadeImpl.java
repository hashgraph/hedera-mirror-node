package com.hedera.mirror.web3.evm.contracts.execution;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.ccps;
import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.gasCalculator;
import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.mcps;

import java.time.Instant;
import javax.inject.Named;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.web3.evm.account.AccountAccessorImpl;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.StaticBlockMetaSource;
import com.hedera.mirror.web3.evm.store.contracts.MirrorEntityAccess;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.execution.traceability.DefaultHederaTracer;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldState;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

@Named
public class MirrorEvmTxProcessorFacadeImpl implements MirrorEvmTxProcessorFacade {

    private MirrorNodeEvmProperties evmProperties;
    private StaticBlockMetaSource blockMetaSource;
    private MirrorEvmContractAliases aliasManager;
    private PricesAndFeesImpl pricesAndFees;
    private AbstractCodeCache codeCache;
    private HederaEvmMutableWorldState worldState;

    public MirrorEvmTxProcessorFacadeImpl(
            final MirrorEntityAccess entityAccess,
            final MirrorNodeEvmProperties evmProperties,
            final StaticBlockMetaSource blockMetaSource,
            final MirrorEvmContractAliases aliasManager,
            final PricesAndFeesImpl pricesAndFees,
            final AccountAccessorImpl accountAccessor) {
        this.evmProperties = evmProperties;
        this.blockMetaSource = blockMetaSource;
        this.aliasManager = aliasManager;
        this.pricesAndFees = pricesAndFees;

        final int expirationCacheTime = (int) evmProperties.getExpirationCacheTime().toSeconds();

        this.codeCache = new AbstractCodeCache(expirationCacheTime,
                entityAccess);
        this.worldState =
                new HederaEvmWorldState(
                        entityAccess, evmProperties,
                        codeCache, accountAccessor, null);
    }

    @Override
    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final boolean isStatic) {
        final var processor =
                new MirrorEvmTxProcessor(
                        worldState,
                        pricesAndFees,
                        evmProperties,
                        gasCalculator,
                        mcps(),
                        ccps(),
                        blockMetaSource,
                        aliasManager,
                        codeCache);

        processor.setOperationTracer(new DefaultHederaTracer());

        return processor.execute(
                sender,
                receiver,
                providedGasLimit,
                value,
                callData,
                Instant.now(),
                isStatic);
    }
}
