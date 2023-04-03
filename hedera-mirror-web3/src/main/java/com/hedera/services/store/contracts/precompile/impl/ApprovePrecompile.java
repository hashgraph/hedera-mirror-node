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
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BOOL;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT_BOOL_PAIR;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrueOrRevert;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.utils.GasCostType.APPROVE;
import static com.hedera.services.store.contracts.precompile.utils.GasCostType.DELETE_NFT_APPROVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;

import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.contracts.MirrorState;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;

public class ApprovePrecompile extends AbstractWritePrecompile {

    private static final String ADDRESS_ADDRESS_UINT256_RAW_TYPE = "(bytes32,bytes32,uint256)";
    private static final Function ERC_TOKEN_APPROVE_FUNCTION = new Function("approve(address,uint256)", BOOL);
    private static final Bytes ERC_TOKEN_APPROVE_SELECTOR = Bytes.wrap(ERC_TOKEN_APPROVE_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TOKEN_APPROVE_DECODER = TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_TOKEN_APPROVE_FUNCTION =
            new Function("approve(address,address,uint256)", INT_BOOL_PAIR);
    private static final Bytes HAPI_TOKEN_APPROVE_SELECTOR = Bytes.wrap(HAPI_TOKEN_APPROVE_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_TOKEN_APPROVE_DECODER =
            TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_APPROVE_NFT_FUNCTION = new Function("approveNFT(address,address,uint256)", INT);
    private static final Bytes HAPI_APPROVE_NFT_SELECTOR = Bytes.wrap(HAPI_APPROVE_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_APPROVE_NFT_DECODER = TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);

    private final TokenID tokenId;
    private final boolean isFungible;
    private final EncodingFacade encoder;
    private final Address senderAddress;
    private ApproveWrapper approveOp;

    @Nullable
    private EntityId operatorId;

    @Nullable
    private EntityId ownerId;

    public ApprovePrecompile(
            final TokenID tokenId,
            final boolean isFungible,
            final MirrorState mirrorState,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils,
            final Address senderAddress) {
        super(mirrorState, pricingUtils);
        this.tokenId = tokenId;
        this.isFungible = isFungible;
        this.encoder = encoder;
        this.senderAddress = senderAddress;
    }

    public ApprovePrecompile(
            final boolean isFungible,
            final MirrorState mirrorState,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils,
            final Address senderAddress) {
        this(
                null,
                isFungible,
                mirrorState,
                encoder,
                pricingUtils,
                senderAddress);
    }

    @Override
    public void body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var nestedInput = tokenId == null ? input : input.slice(24);
        approveOp = decodeTokenApprove(nestedInput, tokenId, isFungible, aliasResolver, mirrorState);
        operatorId = EntityId.fromAddress(senderAddress);
        if (approveOp.isFungible()) {
//            transactionBody = syntheticTxnFactory.createFungibleApproval(approveOp);
        } else {
//            final var nftId =
//                    NftId.fromGrpc(approveOp.tokenId(), approveOp.serialNumber().longValueExact());
//            ownerId = mirrorState.ownerIfPresent(nftId);
            if (isNftApprovalRevocation()) {
                final var nominalOwnerId = ownerId != null ? ownerId : MISSING_ENTITY_ID;
//                transactionBody = syntheticTxnFactory.createDeleteAllowance(approveOp, nominalOwnerId);
            } else {
//                transactionBody = syntheticTxnFactory.createNonfungibleApproval(approveOp, ownerId, operatorId);
            }
        }
    }

    @Override
    public void run(final MessageFrame frame) {
        Objects.requireNonNull(approveOp, "`body` method should be called before `run`");

        validateTrueOrRevert(approveOp.isFungible() || ownerId != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
        final var grpcOperatorId = Objects.requireNonNull(operatorId).toGrpcAccountId();
        //  Per the ERC-721 spec, "Throws unless `msg.sender` is the current NFT owner, or
        //  an authorized operator of the current owner"
        if (!approveOp.isFungible()) {
            final var isApproved = operatorId.equals(ownerId)
                    || mirrorState.hasApprovedForAll(ownerId.toGrpcAccountId(), grpcOperatorId, approveOp.tokenId());
            validateTrueOrRevert(isApproved, SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
        }

        final var tokenAddress = asTypedEvmAddress(approveOp.tokenId());
        if (approveOp.isFungible()) {
            frame.addLog(getLogForFungibleAdjustAllowance(tokenAddress));
        } else {
            frame.addLog(getLogForNftAdjustAllowance(tokenAddress));
        }
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        if (isNftApprovalRevocation()) {
            return pricingUtils.getMinimumPriceInTinybars(DELETE_NFT_APPROVE, consensusTime);
        } else {
            return pricingUtils.getMinimumPriceInTinybars(APPROVE, consensusTime);
        }
    }

    @Override
    public Bytes getSuccessResultFor() {
        if (tokenId != null) {
            return encoder.encodeApprove(true);
        } else if (isFungible) {
            return encoder.encodeApprove(SUCCESS.getNumber(), true);
        } else {
            return encoder.encodeApproveNFT(SUCCESS.getNumber());
        }
    }

    public static ApproveWrapper decodeTokenApprove(
            final Bytes input,
            final TokenID impliedTokenId,
            final boolean isFungible,
            final UnaryOperator<byte[]> aliasResolver,
            final MirrorState mirrorState) {

        final var offset = impliedTokenId == null ? 1 : 0;
        final Tuple decodedArguments;
        final TokenID tokenId;

        if (offset == 0) {
            decodedArguments = decodeFunctionCall(input, ERC_TOKEN_APPROVE_SELECTOR, ERC_TOKEN_APPROVE_DECODER);
            tokenId = impliedTokenId;
        } else if (isFungible) {
            decodedArguments = decodeFunctionCall(input, HAPI_TOKEN_APPROVE_SELECTOR, HAPI_TOKEN_APPROVE_DECODER);
            tokenId = convertAddressBytesToTokenID(decodedArguments.get(0));
        } else {
            decodedArguments = decodeFunctionCall(input, HAPI_APPROVE_NFT_SELECTOR, HAPI_APPROVE_NFT_DECODER);
            tokenId = convertAddressBytesToTokenID(decodedArguments.get(0));
        }

        final var ledgerFungible = TokenType.FUNGIBLE_COMMON.equals(mirrorState.typeOf(tokenId));
        final var spender = convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);

        if (isFungible) {
            if (!ledgerFungible) {
                throw new IllegalArgumentException("Token is not a fungible token");
            }
            final var amount = (BigInteger) decodedArguments.get(offset + 1);

            return new ApproveWrapper(tokenId, spender, amount, BigInteger.ZERO, true);
        } else {
            if (ledgerFungible) {
                throw new IllegalArgumentException("Token is not an NFT");
            }
            final var serialNumber = (BigInteger) decodedArguments.get(offset + 1);

            return new ApproveWrapper(tokenId, spender, BigInteger.ZERO, serialNumber, false);
        }
    }

    private boolean isNftApprovalRevocation() {
        return Objects.requireNonNull(approveOp, "`body` method should be called before `isNftApprovalRevocation`")
                .spender()
                .getAccountNum()
                == 0;
    }

    private Log getLogForFungibleAdjustAllowance(final Address logger) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(mirrorState.canonicalAddress(senderAddress))
                .forIndexedArgument(mirrorState.canonicalAddress(asTypedEvmAddress(approveOp.spender())))
                .forDataItem(approveOp.amount())
                .build();
    }

    private Log getLogForNftAdjustAllowance(final Address logger) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(mirrorState.canonicalAddress(senderAddress))
                .forIndexedArgument(mirrorState.canonicalAddress(asTypedEvmAddress(approveOp.spender())))
                .forIndexedArgument(approveOp.serialNumber())
                .build();
    }
}
