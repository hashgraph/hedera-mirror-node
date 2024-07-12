/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.service.evm.contracts.execution.BlockMetaSource;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTxProcessor;
import com.hedera.node.app.service.evm.contracts.execution.PricesAndFeesProvider;
import com.hedera.node.app.service.evm.contracts.execution.traceability.HederaEvmOperationTracer;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.models.Account;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.utility.SemanticVersion;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Map;
import javax.inject.Provider;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

@Named
public class MirrorEvmTxProcessorImpl extends HederaEvmTxProcessor implements MirrorEvmTxProcessor {

    private final AbstractCodeCache codeCache;
    private final MirrorEvmContractAliases aliasManager;
    private final Store store;
    private final EntityAddressSequencer entityAddressSequencer;
    private final TokenAccessor tokenAccessor;

    @SuppressWarnings("java:S107")
    public MirrorEvmTxProcessorImpl(
            final HederaEvmMutableWorldState worldState,
            final PricesAndFeesProvider pricesAndFeesProvider,
            final EvmProperties dynamicProperties,
            final GasCalculator gasCalculator,
            final Map<SemanticVersion, Provider<MessageCallProcessor>> mcps,
            final Map<SemanticVersion, Provider<ContractCreationProcessor>> ccps,
            final BlockMetaSource blockMetaSource,
            final MirrorEvmContractAliases aliasManager,
            final AbstractCodeCache codeCache,
            final Map<TracerType, Provider<HederaEvmOperationTracer>> tracerMap,
            final Store store,
            final EntityAddressSequencer entityAddressSequencer,
            final TokenAccessor tokenAccessor) {
        super(
                worldState,
                pricesAndFeesProvider,
                dynamicProperties,
                gasCalculator,
                mcps,
                ccps,
                blockMetaSource,
                tracerMap);

        this.aliasManager = aliasManager;
        this.codeCache = codeCache;
        this.store = store;
        this.entityAddressSequencer = entityAddressSequencer;
        this.tokenAccessor = tokenAccessor;
    }

    public HederaEvmTransactionProcessingResult execute(final CallServiceParameters params, final long estimatedGas) {
        final long gasPrice = gasPriceTinyBarsGiven(Instant.now());

        store.wrap();
        if (store.getAccount(params.getSender().canonicalAddress(), OnMissing.DONT_THROW)
                .isEmptyAccount()) {
            final var senderAccount =
                    Account.getDummySenderAccount(params.getSender().canonicalAddress());
            store.updateAccount(senderAccount);
        }
        Address receiverAddress = determineReceiverAddress(params.getReceiver());
        return super.execute(
                params.getSender(),
                receiverAddress,
                gasPrice,
                params.isEstimate(),
                params.isEstimate() ? estimatedGas : params.getGas(),
                params.getValue(),
                params.getCallData(),
                params.isStatic(),
                aliasManager.resolveForEvm(params.getReceiver()),
                params.getReceiver().equals(Address.ZERO),
                params.getTracerType());
    }

    @Override
    protected MessageFrame buildInitialFrame(
            final MessageFrame.Builder baseInitialFrame, final Address to, final Bytes payload, long value) {
        if (Address.ZERO.equals(to)) {
            var contractAddress = EntityIdUtils.asTypedEvmAddress(entityAddressSequencer.getNewContractId(to));
            return baseInitialFrame
                    .type(MessageFrame.Type.CONTRACT_CREATION)
                    .address(contractAddress)
                    .contract(contractAddress)
                    .inputData(Bytes.EMPTY)
                    .code(CodeFactory.createCode(payload, 0, false))
                    .build();
        } else {
            final var resolvedForEvm = aliasManager.resolveForEvm(to);
            final var code = aliasManager.isMirror(resolvedForEvm) ? codeCache.getIfPresent(resolvedForEvm) : null;
            final var isNotCallingNativePrecompile = !aliasManager.isNativePrecompileAddress(resolvedForEvm);

            // If there is no bytecode, it means we have a non-token and non-contract account,
            // hence the code should be null and there must be a value transfer.
            if (!dynamicProperties.callsToNonExistingEntitiesEnabled(to)
                    && code == null
                    && value <= 0
                    && !payload.isEmpty()
                    && isNotCallingNativePrecompile) {
                throw new MirrorEvmTransactionException(
                        ResponseCodeEnum.INVALID_TRANSACTION, StringUtils.EMPTY, StringUtils.EMPTY);
            }

            return baseInitialFrame
                    .type(MessageFrame.Type.MESSAGE_CALL)
                    .address(to)
                    .contract(to)
                    .inputData(payload)
                    .code(code == null ? CodeV0.EMPTY_CODE : code)
                    .build();
        }
    }

    private Address determineReceiverAddress(Address receiver) {
        if (receiver == null || receiver.equals(Address.ZERO) || !aliasManager.isMirror(receiver)) {
            return receiver;
        }

        Address canonical = tokenAccessor.canonicalAddress(receiver);
        if (canonical == null || Address.ZERO.equals(canonical)) {
            return receiver;
        } else {
            return canonical;
        }
    }
}
