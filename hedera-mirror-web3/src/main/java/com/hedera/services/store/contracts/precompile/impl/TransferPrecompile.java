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
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_FUNGIBLE_TRANSFERS;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_NFT_EXCHANGES;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.bindFungibleTransfersFrom;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.bindHBarTransfersFrom;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.bindNftExchangesFrom;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeAccountIds;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.generateAccountIDWithAliasCalculatedFrom;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.CryptoTransferWrapper;
import com.hedera.services.store.contracts.precompile.FungibleTokenTransfer;
import com.hedera.services.store.contracts.precompile.HbarTransfer;
import com.hedera.services.store.contracts.precompile.NftExchange;
import com.hedera.services.store.contracts.precompile.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.TransferWrapper;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TransferPrecompile extends AbstractWritePrecompile {
    private static final Function CRYPTO_TRANSFER_FUNCTION =
            new Function("cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])", INT);
    private static final Function CRYPTO_TRANSFER_FUNCTION_V2 = new Function(
            "cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,"
                    + "bool)[])[])",
            INT);
    private static final Bytes CRYPTO_TRANSFER_SELECTOR = Bytes.wrap(CRYPTO_TRANSFER_FUNCTION.selector());
    private static final Bytes CRYPTO_TRANSFER_SELECTOR_V2 = Bytes.wrap(CRYPTO_TRANSFER_FUNCTION_V2.selector());
    private static final ABIType<Tuple> CRYPTO_TRANSFER_DECODER =
            TypeFactory.create("((bytes32,(bytes32,int64)[],(bytes32,bytes32,int64)[])[])");
    private static final ABIType<Tuple> CRYPTO_TRANSFER_DECODER_V2 = TypeFactory.create(
            "(((bytes32,int64,bool)[]),(bytes32,(bytes32,int64,bool)[],(bytes32,bytes32,int64,bool)[])[])");
    private static final Function TRANSFER_TOKENS_FUNCTION =
            new Function("transferTokens(address,address[],int64[])", INT);
    private static final Bytes TRANSFER_TOKENS_SELECTOR = Bytes.wrap(TRANSFER_TOKENS_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_TOKENS_DECODER = TypeFactory.create("(bytes32,bytes32[],int64[])");
    private static final Function TRANSFER_TOKEN_FUNCTION =
            new Function("transferToken(address,address,address,int64)", INT);
    private static final Bytes TRANSFER_TOKEN_SELECTOR = Bytes.wrap(TRANSFER_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_TOKEN_DECODER = TypeFactory.create("(bytes32,bytes32,bytes32,int64)");
    private static final Function TRANSFER_NFTS_FUNCTION =
            new Function("transferNFTs(address,address[],address[],int64[])", INT);
    private static final Bytes TRANSFER_NFTS_SELECTOR = Bytes.wrap(TRANSFER_NFTS_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_NFTS_DECODER =
            TypeFactory.create("(bytes32,bytes32[],bytes32[],int64[])");
    private static final Function TRANSFER_NFT_FUNCTION =
            new Function("transferNFT(address,address,address,int64)", INT);
    private static final Bytes TRANSFER_NFT_SELECTOR = Bytes.wrap(TRANSFER_NFT_FUNCTION.selector());
    private static final ABIType<Tuple> TRANSFER_NFT_DECODER = TypeFactory.create("(bytes32,bytes32,bytes32,int64)");

    protected CryptoTransferWrapper transferOp;
    private final int functionId;

    private final boolean isLazyCreationEnabled;
    private ImpliedTransfers impliedTransfers;
    private int numLazyCreates;

    public TransferPrecompile(PrecompilePricingUtils pricingUtils, int functionId, boolean isLazyCreationEnabled) {
        super(pricingUtils);
        this.functionId = functionId;
        this.isLazyCreationEnabled = isLazyCreationEnabled;
    }

    @Override
    public void body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        transferOp = switch (functionId) {
            case AbiConstants.ABI_ID_CRYPTO_TRANSFER -> decodeCryptoTransfer(input, aliasResolver);
            case AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2 -> decodeCryptoTransferV2(input, aliasResolver);
            case AbiConstants.ABI_ID_TRANSFER_TOKENS -> decodeTransferTokens(input, aliasResolver);
            case AbiConstants.ABI_ID_TRANSFER_TOKEN -> decodeTransferToken(input, aliasResolver);
            case AbiConstants.ABI_ID_TRANSFER_NFTS -> decodeTransferNFTs(input, aliasResolver);
            case AbiConstants.ABI_ID_TRANSFER_NFT -> decodeTransferNFT(input, aliasResolver);
            default -> null;};
        Objects.requireNonNull(transferOp, "Unable to decode function input");
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        Objects.requireNonNull(transferOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        long accumulatedCost = 0;
        final boolean customFees = impliedTransfers != null && impliedTransfers.hasAssessedCustomFees();
        // For fungible there are always at least two operations, so only charge half for each
        // operation
        final long ftTxCost = pricingUtils.getMinimumPriceInTinybars(
                        customFees
                                ? PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE_CUSTOM_FEES
                                : PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE,
                        consensusTime)
                / 2;
        // NFTs are atomic, one line can do it.
        final long nonFungibleTxCost = pricingUtils.getMinimumPriceInTinybars(
                customFees
                        ? PrecompilePricingUtils.GasCostType.TRANSFER_NFT_CUSTOM_FEES
                        : PrecompilePricingUtils.GasCostType.TRANSFER_NFT,
                consensusTime);
        for (final var transfer : transferOp.tokenTransferWrappers()) {
            accumulatedCost += transfer.fungibleTransfers().size() * ftTxCost;
            accumulatedCost += transfer.nftExchanges().size() * nonFungibleTxCost;
        }

        // add the cost for transferring hbars
        // Hbar transfer is similar to fungible tokens so only charge half for each operation
        final long hbarTxCost =
                pricingUtils.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.TRANSFER_HBAR, consensusTime)
                        / 2;
        accumulatedCost += transferOp.transferWrapper().hbarTransfers().size() * hbarTxCost;
        if (isLazyCreationEnabled && numLazyCreates > 0) {
            final var lazyCreationFee = pricingUtils.getMinimumPriceInTinybars(GasCostType.CRYPTO_CREATE, consensusTime)
                    + pricingUtils.getMinimumPriceInTinybars(GasCostType.CRYPTO_UPDATE, consensusTime);
            accumulatedCost += numLazyCreates * lazyCreationFee;
        }
        return accumulatedCost;
    }

    @Override
    public void run(MessageFrame frame) {}

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(functionId);
    }

    /**
     * Decodes the given bytes of the cryptoTransfer function parameters
     *
     * <p><b>Important: </b>This is the latest version and supersedes public static
     * CryptoTransferWrapper decodeCryptoTransfer(). The selector for this function is derived from:
     * cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[])
     * The first parameter describes hbar transfers and the second describes token transfers
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return CryptoTransferWrapper codec
     */
    public static CryptoTransferWrapper decodeCryptoTransferV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedTuples = decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR_V2, CRYPTO_TRANSFER_DECODER_V2);
        List<HbarTransfer> hbarTransfers = new ArrayList<>();
        final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

        final Tuple[] hbarTransferTuples = ((Tuple) decodedTuples.get(0)).get(0);
        final var tokenTransferTuples = decodedTuples.get(1);

        hbarTransfers = decodeHbarTransfers(aliasResolver, hbarTransfers, hbarTransferTuples);

        decodeTokenTransfer(aliasResolver, tokenTransferWrappers, (Tuple[]) tokenTransferTuples);

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static List<HbarTransfer> decodeHbarTransfers(
            final UnaryOperator<byte[]> aliasResolver,
            List<HbarTransfer> hbarTransfers,
            final Tuple[] hbarTransferTuples) {
        if (hbarTransferTuples.length > 0) {
            hbarTransfers = bindHBarTransfersFrom(hbarTransferTuples, aliasResolver);
        }
        return hbarTransfers;
    }

    /**
     * Decodes the given bytes of the cryptoTransfer function parameters
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeCryptoTransferV2(). The selector for this function is derived from:
     * cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])
     *
     * @param input encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return CryptoTransferWrapper codec
     */
    public static CryptoTransferWrapper decodeCryptoTransfer(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedTuples = decodeFunctionCall(input, CRYPTO_TRANSFER_SELECTOR, CRYPTO_TRANSFER_DECODER);

        final List<TokenTransferWrapper> tokenTransferWrappers = new ArrayList<>();

        for (final var tuple : decodedTuples) {
            decodeTokenTransfer(aliasResolver, tokenTransferWrappers, (Tuple[]) tuple);
        }

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static void decodeTokenTransfer(
            final UnaryOperator<byte[]> aliasResolver,
            final List<TokenTransferWrapper> tokenTransferWrappers,
            final Tuple[] tokenTransferTuples) {
        for (final var tupleNested : tokenTransferTuples) {
            final var tokenType = convertAddressBytesToTokenID(tupleNested.get(0));

            var nftExchanges = NO_NFT_EXCHANGES;
            var fungibleTransfers = NO_FUNGIBLE_TRANSFERS;

            final var abiAdjustments = (Tuple[]) tupleNested.get(1);
            if (abiAdjustments.length > 0) {
                fungibleTransfers = bindFungibleTransfersFrom(tokenType, abiAdjustments, aliasResolver);
            }
            final var abiNftExchanges = (Tuple[]) tupleNested.get(2);
            if (abiNftExchanges.length > 0) {
                nftExchanges = bindNftExchangesFrom(tokenType, abiNftExchanges, aliasResolver);
            }

            tokenTransferWrappers.add(new TokenTransferWrapper(nftExchanges, fungibleTransfers));
        }
    }

    public static CryptoTransferWrapper decodeTransferToken(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKEN_SELECTOR, TRANSFER_TOKEN_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var sender = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var receiver = convertLeftPaddedAddressToAccountId(decodedArguments.get(2), aliasResolver);
        final var amount = (long) decodedArguments.get(3);

        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }

        final var tokenTransferWrappers = Collections.singletonList(new TokenTransferWrapper(
                NO_NFT_EXCHANGES, List.of(new FungibleTokenTransfer(amount, false, tokenID, sender, receiver))));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferTokens(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_TOKENS_SELECTOR, TRANSFER_TOKENS_DECODER);

        final var tokenType = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountIDs = decodeAccountIds(decodedArguments.get(1), aliasResolver);
        final var amounts = (long[]) decodedArguments.get(2);

        final List<FungibleTokenTransfer> fungibleTransfers = new ArrayList<>();
        addSignedAdjustments(fungibleTransfers, accountIDs, tokenType, amounts);

        final var tokenTransferWrappers =
                Collections.singletonList(new TokenTransferWrapper(NO_NFT_EXCHANGES, fungibleTransfers));

        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferNFTs(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFTS_SELECTOR, TRANSFER_NFTS_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var senders = decodeAccountIds(decodedArguments.get(1), aliasResolver);
        final var receivers = decodeAccountIds(decodedArguments.get(2), aliasResolver);
        final var serialNumbers = ((long[]) decodedArguments.get(3));

        final List<NftExchange> nftExchanges = new ArrayList<>();
        addNftExchanges(nftExchanges, senders, receivers, serialNumbers, tokenID);

        final var tokenTransferWrappers =
                Collections.singletonList(new TokenTransferWrapper(nftExchanges, NO_FUNGIBLE_TRANSFERS));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static CryptoTransferWrapper decodeTransferNFT(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final List<HbarTransfer> hbarTransfers = Collections.emptyList();
        final Tuple decodedArguments = decodeFunctionCall(input, TRANSFER_NFT_SELECTOR, TRANSFER_NFT_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var sender = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var receiver = convertLeftPaddedAddressToAccountId(decodedArguments.get(2), aliasResolver);
        final var serialNumber = (long) decodedArguments.get(3);

        final var tokenTransferWrappers = Collections.singletonList(new TokenTransferWrapper(
                List.of(new NftExchange(serialNumber, tokenID, sender, receiver)), NO_FUNGIBLE_TRANSFERS));
        return new CryptoTransferWrapper(new TransferWrapper(hbarTransfers), tokenTransferWrappers);
    }

    public static void addNftExchanges(
            final List<NftExchange> nftExchanges,
            final List<AccountID> senders,
            final List<AccountID> receivers,
            final long[] serialNumbers,
            final TokenID tokenID) {
        for (var i = 0; i < senders.size(); i++) {
            var receiver = receivers.get(i);
            if (!receiver.hasAlias()) {
                receiver = generateAccountIDWithAliasCalculatedFrom(receiver);
            }
            final var nftExchange = new NftExchange(serialNumbers[i], tokenID, senders.get(i), receiver);
            nftExchanges.add(nftExchange);
        }
    }

    public static void addSignedAdjustments(
            final List<FungibleTokenTransfer> fungibleTransfers,
            final List<AccountID> accountIDs,
            final TokenID tokenType,
            final long[] amounts) {
        for (int i = 0; i < accountIDs.size(); i++) {
            final var amount = amounts[i];

            var accountID = accountIDs.get(i);
            if (amount > 0 && !accountID.hasAlias()) {
                accountID = generateAccountIDWithAliasCalculatedFrom(accountID);
            }

            DecodingFacade.addSignedAdjustment(fungibleTransfers, tokenType, accountID, amount, false);
        }
    }
}
