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

import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmBalanceOfPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectTarget;
import com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BalanceOfResult;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class BalanceOfPrecompile extends AbstractReadOnlyPrecompile implements EvmBalanceOfPrecompile {
    public BalanceOfPrecompile(
            SyntheticTxnFactory syntheticTxnFactory, EncodingFacade encoder, PrecompilePricingUtils pricingUtils) {
        super(syntheticTxnFactory, encoder, pricingUtils);
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var balanceOp = frame.getInputData();
        RedirectTarget target;
        try {
            target = DescriptorUtils.getRedirectTarget(balanceOp);
        } catch (final Exception e) {
            throw new InvalidTransactionException(ResponseCodeEnum.ERROR_DECODING_BYTESTRING);
        }
        final var accountAddress =
                DescriptorUtils.addressFromBytes(balanceOp.slice(40).toArray());
        final var tokenAddress = target.token();
        final var tokenRel =
                store.getTokenRelationship(new TokenRelationshipKey(tokenAddress, accountAddress), OnMissing.THROW);
        final var balance = tokenRel.getBalance();
        return new BalanceOfResult(balance);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_ERC_BALANCE_OF_TOKEN);
    }

    @Override
    public Bytes getSuccessResultFor(RunResult runResult) {
        final var balanceOfResult = (BalanceOfResult) runResult;
        return encoder.encodeBalance(balanceOfResult.balance());
    }
}
