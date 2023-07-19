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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_SET_APPROVAL_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_SET_APPROVAL_FOR_ALL;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.APPROVE;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.google.protobuf.BoolValue;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.HrcParams;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.SetApprovalForAllWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.crypto.ApproveAllowanceLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public class SetApprovalForAllPrecompile extends AbstractWritePrecompile {
    private static final Function ERC_SET_APPROVAL_FOR_ALL = new Function("setApprovalForAll(address,bool)");
    private static final Bytes ERC_SET_APPROVAL_FOR_ALL_SELECTOR = Bytes.wrap(ERC_SET_APPROVAL_FOR_ALL.selector());
    private static final ABIType<Tuple> ERC_SET_APPROVAL_FOR_ALL_DECODER = TypeFactory.create("(bytes32,bool)");
    private static final Function HAPI_SET_APPROVAL_FOR_ALL =
            new Function("setApprovalForAll(address,address,bool)", INT);
    private static final Bytes HAPI_SET_APPROVAL_FOR_ALL_SELECTOR = Bytes.wrap(HAPI_SET_APPROVAL_FOR_ALL.selector());
    private static final ABIType<Tuple> HAPI_SET_APPROVAL_FOR_ALL_DECODER =
            TypeFactory.create("(bytes32,bytes32,bool)");
    private final ApproveAllowanceChecks approveAllowanceChecks;
    private final ApproveAllowanceLogic approveAllowanceLogic;

    public SetApprovalForAllPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final ApproveAllowanceChecks approveAllowanceChecks,
            final ApproveAllowanceLogic approveAllowanceLogic) {
        super(pricingUtils, syntheticTxnFactory);
        this.approveAllowanceChecks = approveAllowanceChecks;
        this.approveAllowanceLogic = approveAllowanceLogic;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        TokenID tokenId = null;
        Address senderAddress = Address.ZERO;

        if (bodyParams instanceof HrcParams hrcParams) {
            tokenId = hrcParams.token();
            senderAddress = hrcParams.senderAddress();
        }
        final var nestedInput = tokenId == null ? input : input.slice(24);
        final var ownerId = Id.fromGrpcAccount(EntityIdUtils.accountIdFromEvmAddress(senderAddress));
        final var setApprovalForAllWrapper = decodeSetApprovalForAll(nestedInput, tokenId, aliasResolver);
        return syntheticTxnFactory.createApproveAllowanceForAllNFT(setApprovalForAllWrapper, ownerId);
    }

    @Override
    public RunResult run(final MessageFrame frame, TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `run`");

        final var updater = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater());
        final var store = updater.getStore();

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var payerAccount = store.getAccount(frame.getSenderAddress(), OnMissing.THROW);

        final var status = approveAllowanceChecks.allowancesValidation(
                transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                payerAccount,
                store);
        validateTrueOrRevert(status == OK, status);

        /* --- Execute the transaction and capture its results --- */
        approveAllowanceLogic.approveAllowance(
                store,
                new TreeMap<>(),
                new TreeMap<>(),
                transactionBody.getCryptoApproveAllowance().getCryptoAllowancesList(),
                transactionBody.getCryptoApproveAllowance().getTokenAllowancesList(),
                transactionBody.getCryptoApproveAllowance().getNftAllowancesList(),
                EntityIdUtils.accountIdFromEvmAddress(frame.getSenderAddress()));
        final var nftAllowances = transactionBody.getCryptoApproveAllowance().getNftAllowances(0);
        final var tokenAddress = asTypedEvmAddress(nftAllowances.getTokenId());
        final var spenderAddress = asTypedEvmAddress(nftAllowances.getSpender());
        final var approved = nftAllowances.getApprovedForAll();
        frame.addLog(
                getLogForSetApprovalForAll(tokenAddress, frame.getSenderAddress(), spenderAddress, approved, updater));
        return new EmptyRunResult();
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime, final TransactionBody transactionBody) {
        return pricingUtils.getMinimumPriceInTinybars(APPROVE, consensusTime);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_ERC_SET_APPROVAL_FOR_ALL, ABI_ID_SET_APPROVAL_FOR_ALL);
    }

    private Log getLogForSetApprovalForAll(
            final Address logger,
            final Address senderAddress,
            final Address to,
            final BoolValue approved,
            final HederaEvmStackedWorldStateUpdater updater) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_FOR_ALL_EVENT)
                .forIndexedArgument(updater.priorityAddress(senderAddress))
                .forIndexedArgument(updater.priorityAddress(to))
                .forDataItem(approved)
                .build();
    }

    public static SetApprovalForAllWrapper decodeSetApprovalForAll(
            final Bytes input, final TokenID impliedTokenId, final UnaryOperator<byte[]> aliasResolver) {
        final var offset = impliedTokenId == null ? 1 : 0;
        final Tuple decodedArguments = decodeFunctionCall(
                input,
                offset == 0 ? ERC_SET_APPROVAL_FOR_ALL_SELECTOR : HAPI_SET_APPROVAL_FOR_ALL_SELECTOR,
                offset == 0 ? ERC_SET_APPROVAL_FOR_ALL_DECODER : HAPI_SET_APPROVAL_FOR_ALL_DECODER);
        final var tokenId = offset == 0 ? impliedTokenId : convertAddressBytesToTokenID(decodedArguments.get(0));

        final var to = convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);
        final var approved = (boolean) decodedArguments.get(offset + 1);

        return new SetApprovalForAllWrapper(tokenId, to, approved);
    }
}
