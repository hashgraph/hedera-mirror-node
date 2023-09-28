/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.common.ThreadLocalHolder.isCreate;

import com.hedera.mirror.web3.common.ThreadLocalHolder;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.MirrorOperationTracer;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.node.app.service.evm.contracts.execution.BlockMetaSource;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTxProcessor;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.Map;
import javax.inject.Provider;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

public class MirrorEvmTxProcessor extends HederaEvmTxProcessor {

    private final AbstractCodeCache codeCache;
    private final MirrorEvmContractAliases aliasManager;
    private final MirrorOperationTracer operationTracer;
    private final Store store;
    private final boolean isCreate;

    @SuppressWarnings("java:S107")
    public MirrorEvmTxProcessor(
            final HederaEvmMutableWorldState worldState,
            final PricesAndFeesProvider pricesAndFeesProvider,
            final EvmProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<String, Provider<MessageCallProcessor>> mcps,
            final Map<String, Provider<ContractCreationProcessor>> ccps,
            final BlockMetaSource blockMetaSource,
            final MirrorEvmContractAliases aliasManager,
            final AbstractCodeCache codeCache,
            final MirrorOperationTracer operationTracer,
            final Store store) {
        super(worldState, pricesAndFeesProvider, dynamicProperties, gasCalculator, mcps, ccps, blockMetaSource);

        this.aliasManager = aliasManager;
        this.codeCache = codeCache;
        this.operationTracer = operationTracer;
        this.store = store;
        this.isCreate = ThreadLocalHolder.isCreate();
    }

    @SuppressWarnings("java:S107")
    public HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData,
            final Instant consensusTime,
            final boolean isStatic,
            final boolean isEstimate) {
        final long gasPrice = gasPriceTinyBarsGiven(consensusTime, true);
        // in cases where the receiver is the zero address, we know it's a contract create scenario
        super.setupFields(receiver.equals(Address.ZERO));
        super.setOperationTracer(operationTracer);
        setIsCreate(Address.ZERO.equals(receiver));
        setIsEstimate(isEstimate);

        store.wrap();
        if (isEstimate) {
            final var defaultAccount = Account.getDefaultAccount();
            store.updateAccount(defaultAccount);
        }

        return super.execute(
                sender,
                receiver,
                gasPrice,
                providedGasLimit,
                value,
                callData,
                isStatic,
                aliasManager.resolveForEvm(receiver));
    }

    @SuppressWarnings("java:S5411")
    @Override
    protected HederaFunctionality getFunctionType() {
        return isCreate ? HederaFunctionality.ContractCreate : HederaFunctionality.ContractCall;
    }

    @SuppressWarnings("java:S5411")
    @Override
    protected MessageFrame buildInitialFrame(
            final MessageFrame.Builder baseInitialFrame, final Address to, final Bytes payload, long value) {
        final var code = codeCache.getIfPresent(aliasManager.resolveForEvm(to));

        if (code == null) {
            throw new MirrorEvmTransactionException(
                    ResponseCodeEnum.INVALID_TRANSACTION, StringUtils.EMPTY, StringUtils.EMPTY);
        }

        return isCreate
                ? baseInitialFrame
                        .type(MessageFrame.Type.CONTRACT_CREATION)
                        .address(to)
                        .contract(to)
                        .inputData(payload)
                        .inputData(Bytes.EMPTY)
                        .code(CodeFactory.createCode(payload, 0, false))
                        .build()
                : baseInitialFrame
                        .type(MessageFrame.Type.MESSAGE_CALL)
                        .address(to)
                        .contract(to)
                        .inputData(payload)
                        .code(code)
                        .build();
    }

    public void setIsCreate(boolean isCreate) {
        ThreadLocalHolder.setIsCreate(isCreate);
    }

    public void setIsEstimate(boolean isEstimate) {
        ThreadLocalHolder.setIsEstimate(isEstimate);
    }
}
