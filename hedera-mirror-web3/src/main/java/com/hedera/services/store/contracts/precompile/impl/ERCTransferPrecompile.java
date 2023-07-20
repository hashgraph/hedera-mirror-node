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

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_UINT256_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BOOL;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_FUNGIBLE_TRANSFERS;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_NFT_EXCHANGES;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.addSignedAdjustment;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.CryptoTransferWrapper;
import com.hedera.services.store.contracts.precompile.FungibleTokenTransfer;
import com.hedera.services.store.contracts.precompile.HbarTransfer;
import com.hedera.services.store.contracts.precompile.NftExchange;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.TransferWrapper;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.ERCTransferParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TokenTransferResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public class ERCTransferPrecompile extends TransferPrecompile {

    private static final Function ERC_TRANSFER_FUNCTION = new Function("transfer(address,uint256)", BOOL);
    private static final Bytes ERC_TRANSFER_SELECTOR = Bytes.wrap(ERC_TRANSFER_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TRANSFER_DECODER = TypeFactory.create(ADDRESS_UINT256_RAW_TYPE);
    private static final Function ERC_TRANSFER_FROM_FUNCTION = new Function("transferFrom(address,address,uint256)");
    private static final Bytes ERC_TRANSFER_FROM_SELECTOR = Bytes.wrap(ERC_TRANSFER_FROM_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_TRANSFER_FROM_DECODER =
            TypeFactory.create(ADDRESS_ADDRESS_UINT256_RAW_TYPE);
    private static final Function HAPI_TRANSFER_FROM_FUNCTION =
            new Function("transferFrom(address,address,address,uint256)");
    private static final Bytes HAPI_TRANSFER_FROM_SELECTOR = Bytes.wrap(HAPI_TRANSFER_FROM_FUNCTION.selector());
    private static final Function HAPI_TRANSFER_FROM_NFT_FUNCTION =
            new Function("transferFromNFT(address,address,address,uint256)");
    private static final Bytes HAPI_TRANSFER_FROM_NFT_SELECTOR = Bytes.wrap(HAPI_TRANSFER_FROM_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_TRANSFER_FROM_DECODER =
            TypeFactory.create("(bytes32,bytes32,bytes32,uint256)");

    private TokenID tokenID;
    private final EncodingFacade encoder;

    public ERCTransferPrecompile(
            PrecompilePricingUtils pricingUtils,
            MirrorNodeEvmProperties mirrorNodeEvmProperties,
            TransferLogic transferLogic,
            ContextOptionValidator contextOptionValidator,
            AutoCreationLogic autoCreationLogic,
            SyntheticTxnFactory syntheticTxnFactory,
            EncodingFacade encoder) {
        super(
                pricingUtils,
                mirrorNodeEvmProperties,
                transferLogic,
                contextOptionValidator,
                autoCreationLogic,
                syntheticTxnFactory);
        this.encoder = encoder;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        if (bodyParams instanceof ERCTransferParams transferParams) {
            senderAddress = transferParams.sernderAddress();
            tokenID = transferParams.tokenID();
            transferOp = switch (transferParams.functionId()) {
                case AbiConstants.ABI_ID_ERC_TRANSFER -> decodeERCTransfer(
                        input, tokenID, senderAddress, aliasResolver);
                case AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
                        AbiConstants.ABI_ID_TRANSFER_FROM,
                        AbiConstants.ABI_ID_TRANSFER_FROM_NFT -> {
                    final var operatorID = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
                    yield decodeERCTransferFrom(
                            input, tokenID, transferParams.tokenAccessor(), operatorID, aliasResolver);
                }
                default -> null;};
            Objects.requireNonNull(transferOp, "Unable to decode function input");

            transactionBody = syntheticTxnFactory.createCryptoTransfer(transferOp.tokenTransferWrappers());
            extrapolateDetailsFromSyntheticTxn();
            return transactionBody;
        }
        return TransactionBody.newBuilder();
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        Objects.requireNonNull(transferOp, "`body` method should be called before `run`");
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var cryptoTransfer = transactionBody.getCryptoTransfer();
        final var transfer = cryptoTransfer.getTokenTransfersList().get(0);
        final var tokenAddress = EntityIdUtils.asTypedEvmAddress(transfer.getToken());
        final var token = store.getToken(tokenAddress, OnMissing.THROW);
        final var isFungible = TokenType.FUNGIBLE_COMMON.equals(token.getType());
        if (!isFungible) {
            final var nftExchange =
                    transferOp.tokenTransferWrappers().get(0).nftExchanges().get(0);

            final var nftId = NftId.fromGrpc(nftExchange.getTokenType(), nftExchange.getSerialNo());
            store.getUniqueToken(nftId, OnMissing.THROW);
        }
        try {
            super.run(frame, transactionBody);
        } catch (InvalidTransactionException e) {
            throw new InvalidTransactionException(e.getMessage(), e.getDetail(), e.getData());
        }

        if (isFungible) {
            frame.addLog(getLogForFungibleTransfer(tokenAddress, store));
        } else {
            frame.addLog(getLogForNftExchange(tokenAddress, store));
        }
        return new TokenTransferResult(isFungible, tokenID);
    }

    public static CryptoTransferWrapper decodeERCTransfer(
            final Bytes input,
            final TokenID tokenID,
            final Address senderAddress,
            final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, ERC_TRANSFER_SELECTOR, ERC_TRANSFER_DECODER);

        final var recipient = convertLeftPaddedAddressToAccountId(decodedArguments.get(0), aliasResolver);
        final var amount = (BigInteger) decodedArguments.get(1);
        final var caller = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
        final List<FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
        addSignedAdjustment(fungibleTransfers, tokenID, recipient, amount.longValueExact(), false);
        addSignedAdjustment(fungibleTransfers, tokenID, caller, -amount.longValueExact(), false);

        final var tokenTransferWrappers =
                Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeERCTransferFrom(
            final Bytes input,
            final TokenID tokenID,
            final TokenAccessor tokenAccessor,
            final AccountID operatorID,
            final UnaryOperator<byte[]> aliasResolver) {

        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final var offset = tokenID == null ? 1 : 0;
        Tuple decodedArguments;
        final TokenID token;
        if (offset == 0) {
            decodedArguments = decodeFunctionCall(input, ERC_TRANSFER_FROM_SELECTOR, ERC_TRANSFER_FROM_DECODER);
            token = tokenID;
        } else {
            decodedArguments = decodeHapiArguments(input);
            token = convertAddressBytesToTokenID(decodedArguments.get(0));
        }

        final var from = convertLeftPaddedAddressToAccountId(decodedArguments.get(offset), aliasResolver);
        final var to = convertLeftPaddedAddressToAccountId(decodedArguments.get(offset + 1), aliasResolver);
        final var isFungible =
                TokenType.FUNGIBLE_COMMON.equals(tokenAccessor.typeOf(EntityIdUtils.asTypedEvmAddress(token)));
        if (isFungible) {
            final List<FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
            final var amount = (BigInteger) decodedArguments.get(offset + 2);

            addSignedAdjustment(fungibleTransfers, token, to, amount.longValueExact(), false);

            addSignedAdjustment(fungibleTransfers, token, from, -amount.longValueExact(), true);

            final var tokenTransferWrappers =
                    Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));
            return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
        } else {
            final List<NftExchange> nonFungibleTransfers = new ArrayList<>();
            final var serialNo = ((BigInteger) decodedArguments.get(offset + 2)).longValueExact();
            if (operatorID.equals(from)) {
                nonFungibleTransfers.add(new NftExchange(serialNo, token, from, to));
            } else {
                nonFungibleTransfers.add(NftExchange.fromApproval(serialNo, token, from, to));
            }

            final var tokenTransferWrappers =
                    Collections.singletonList(new TokenTransferWrapper(nonFungibleTransfers, NO_FUNGIBLE_TRANSFERS));
            return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
        }
    }

    private static Tuple decodeHapiArguments(Bytes input) {
        final var functionBytes = input.slice(0, 4);
        if (HAPI_TRANSFER_FROM_SELECTOR.equals(functionBytes)) {
            return decodeFunctionCall(input, HAPI_TRANSFER_FROM_SELECTOR, HAPI_TRANSFER_FROM_DECODER);
        } else {
            return decodeFunctionCall(input, HAPI_TRANSFER_FROM_NFT_SELECTOR, HAPI_TRANSFER_FROM_DECODER);
        }
    }

    private Log getLogForFungibleTransfer(final Address logger, final Store store) {
        final var fungibleTransfers = transferOp.tokenTransferWrappers().get(0).fungibleTransfers();
        Address sender = null;
        Address receiver = null;
        BigInteger amount = BigInteger.ZERO;
        for (final var fungibleTransfer : fungibleTransfers) {
            if (fungibleTransfer.sender() != null) {
                sender = EntityIdUtils.asTypedEvmAddress(fungibleTransfer.sender());
            }
            if (fungibleTransfer.receiver() != null) {
                receiver = EntityIdUtils.asTypedEvmAddress(fungibleTransfer.receiver());
                amount = BigInteger.valueOf(fungibleTransfer.amount());
            }
        }

        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                .forIndexedArgument(sender)
                .forIndexedArgument(receiver)
                .forDataItem(amount)
                .build();
    }

    private Log getLogForNftExchange(final Address logger, final Store store) {
        final var nftExchanges = transferOp.tokenTransferWrappers().get(0).nftExchanges();
        final var nftExchange = nftExchanges.get(0).asGrpc();
        final var sender = store.getAccount(asTypedEvmAddress(nftExchange.getSenderAccountID()), OnMissing.THROW);
        final var receiver = store.getAccount(asTypedEvmAddress(nftExchange.getReceiverAccountID()), OnMissing.THROW);
        final var serialNumber = nftExchange.getSerialNumber();

        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.TRANSFER_EVENT)
                .forIndexedArgument(sender)
                .forIndexedArgument(receiver)
                .forIndexedArgument(serialNumber)
                .build();
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var transferRunResult = (TokenTransferResult) runResult;
        if (transferRunResult.tokenID() != null) {
            return transferRunResult.isFungible() ? encoder.encodeEcFungibleTransfer(true) : Bytes.EMPTY;
        } else {
            return super.getSuccessResultFor(runResult);
        }
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(
                AbiConstants.ABI_ID_ERC_TRANSFER,
                AbiConstants.ABI_ID_ERC_TRANSFER_FROM,
                AbiConstants.ABI_ID_TRANSFER_FROM,
                AbiConstants.ABI_ID_TRANSFER_FROM_NFT);
    }
}
