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

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.FAILURE_DURING_LAZY_ACCOUNT_CREATE;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.wrapUnsafely;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmMessageCallProcessor;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.txns.crypto.AbstractAutoCreationLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.Optional;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;

public class MirrorEvmMessageCallProcessor extends HederaEvmMessageCallProcessor {
    private final MirrorEvmContractAliases mirrorEvmContractAliases;
    private final AbstractAutoCreationLogic autoCreationLogic;
    private final EntityAddressSequencer entityAddressSequencer;

    public MirrorEvmMessageCallProcessor(
            final AbstractAutoCreationLogic autoCreationLogic,
            final EntityAddressSequencer entityAddressSequencer,
            final MirrorEvmContractAliases mirrorEvmContractAliases,
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList) {
        super(evm, precompiles, hederaPrecompileList);
        this.autoCreationLogic = autoCreationLogic;
        this.entityAddressSequencer = entityAddressSequencer;
        this.mirrorEvmContractAliases = mirrorEvmContractAliases;
    }

    @Override
    protected void executeLazyCreate(final MessageFrame frame, final OperationTracer operationTracer) {
        final var updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var syntheticBalanceChange = constructSyntheticLazyCreateBalanceChangeFrom(frame);
        final var timestamp = Timestamp.newBuilder()
                .setSeconds(frame.getBlockValues().getTimestamp())
                .build();
        final var lazyCreateResult = autoCreationLogic.create(
                syntheticBalanceChange,
                timestamp,
                updater.getStore(),
                entityAddressSequencer,
                mirrorEvmContractAliases);
        if (lazyCreateResult.getLeft() != ResponseCodeEnum.OK) {
            haltFrameAndTraceCreationResult(frame, operationTracer, FAILURE_DURING_LAZY_ACCOUNT_CREATE);
        } else {
            final var creationFeeInTinybars = lazyCreateResult.getRight();
            final var creationFeeInGas =
                    creationFeeInTinybars / frame.getGasPrice().toLong();
            if (frame.getRemainingGas() < creationFeeInGas) {
                // ledgers won't be committed on unsuccessful frame and StackedContractAliases
                // will revert any new aliases
                haltFrameAndTraceCreationResult(frame, operationTracer, INSUFFICIENT_GAS);
            } else {
                frame.decrementRemainingGas(creationFeeInGas);

                // we do not track auto-creation preceding child record as the mirror node does not
                // maintain child record logic at the moment

                // track the lazy account so it is accessible to the EVM
                updater.trackLazilyCreatedAccount(EntityIdUtils.asTypedEvmAddress(syntheticBalanceChange.accountId()));
            }
        }
    }

    @NonNull
    private BalanceChange constructSyntheticLazyCreateBalanceChangeFrom(final MessageFrame frame) {
        return BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAccountID(AccountID.newBuilder()
                                .setAlias(
                                        wrapUnsafely(frame.getRecipientAddress().toArrayUnsafe()))
                                .build())
                        .build(),
                null);
    }

    private void haltFrameAndTraceCreationResult(
            final MessageFrame frame, final OperationTracer operationTracer, final ExceptionalHaltReason haltReason) {
        frame.decrementRemainingGas(frame.getRemainingGas());
        frame.setState(EXCEPTIONAL_HALT);
        operationTracer.traceAccountCreationResult(frame, Optional.of(haltReason));
    }
}
