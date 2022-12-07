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

import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.ccps;
import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.gasCalculator;
import static com.hedera.mirror.web3.evm.contracts.execution.EvmOperationConstructionUtil.mcps;

import java.time.Instant;
import javax.inject.Named;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;

import com.hedera.node.app.service.evm.contracts.execution.traceability.DefaultHederaTracer;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.web3.evm.account.AccountAccessorImpl;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.StaticBlockMetaSource;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldState;


@Named
public class MirrorEvmTxProcessorFacadeImpl implements MirrorEvmTxProcessorFacade {
    private final MirrorEvmTxProcessor processor;

    public MirrorEvmTxProcessorFacadeImpl(
            MirrorEntityAccess entityAccess,
            MirrorNodeEvmProperties evmProperties,
            StaticBlockMetaSource blockMetaSource,
            MirrorEvmContractAliases aliasManager,
            PricesAndFeesImpl pricesAndFees,
            AccountAccessorImpl accountAccessor) {
        final AbstractCodeCache codeCache = new AbstractCodeCache(evmProperties.getExpirationCacheTime(), entityAccess);
        final HederaEvmMutableWorldState worldState = new HederaEvmWorldState(entityAccess, evmProperties, codeCache,
                accountAccessor);

        processor =
                new MirrorEvmTxProcessor(
                        worldState,
                        pricesAndFees,
                        evmProperties,
                        gasCalculator,
                        mcps(),
                        ccps(),
                        blockMetaSource,
                        aliasManager,
                        entityAccess);

        processor.setOperationTracer(new DefaultHederaTracer());
    }

    @Override
    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final boolean isStatic) {

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
