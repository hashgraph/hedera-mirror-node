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
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFTS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_FUNGIBLE_TRANSFERS;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.NO_NFT_EXCHANGES;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.bindFungibleTransfersFrom;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.bindHBarTransfersFrom;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.bindNftExchangesFrom;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeAccountIds;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.generateAccountIDWithAliasCalculatedFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static java.math.BigInteger.ZERO;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.store.contracts.precompile.CryptoTransferWrapper;
import com.hedera.services.store.contracts.precompile.FungibleTokenTransfer;
import com.hedera.services.store.contracts.precompile.HbarTransfer;
import com.hedera.services.store.contracts.precompile.NftExchange;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenTransferWrapper;
import com.hedera.services.store.contracts.precompile.TransferWrapper;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TransferParams;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
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
    Address senderAddress;
    private ImpliedTransfers impliedTransfers;
    private int numLazyCreates;
    private ResponseCodeEnum impliedValidity;

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final TransferLogic transferLogic;

    private HederaTokenStore hederaTokenStore;
    private final ContextOptionValidator contextOptionValidator;
    private final AutoCreationLogic autoCreationLogic;
    private TransactionBody.Builder transactionBody;

    public TransferPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final TransferLogic transferLogic,
            final ContextOptionValidator contextOptionValidator,
            final AutoCreationLogic autoCreationLogic,
            final SyntheticTxnFactory syntheticTxnFactory) {
        super(pricingUtils, syntheticTxnFactory);
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.transferLogic = transferLogic;
        this.contextOptionValidator = contextOptionValidator;
        this.autoCreationLogic = autoCreationLogic;
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        if (bodyParams instanceof TransferParams transferParams) {
            transferOp = switch (transferParams.functionId()) {
                case ABI_ID_CRYPTO_TRANSFER -> decodeCryptoTransfer(input, aliasResolver);
                case ABI_ID_CRYPTO_TRANSFER_V2 -> decodeCryptoTransferV2(input, aliasResolver);
                case ABI_ID_TRANSFER_TOKENS -> decodeTransferTokens(input, aliasResolver);
                case ABI_ID_TRANSFER_TOKEN -> decodeTransferToken(input, aliasResolver);
                case ABI_ID_TRANSFER_NFTS -> decodeTransferNFTs(input, aliasResolver);
                case ABI_ID_TRANSFER_NFT -> decodeTransferNFT(input, aliasResolver);
                default -> null;};
            Objects.requireNonNull(transferOp, "Unable to decode function input");
            senderAddress = transferParams.sernderAddress();

            transactionBody = syntheticTxnFactory.createCryptoTransfer(transferOp.tokenTransferWrappers());
            if (!transferOp.transferWrapper().hbarTransfers().isEmpty()) {
                transactionBody.mergeFrom(
                        syntheticTxnFactory.createCryptoTransferForHbar(transferOp.transferWrapper()));
            }

            extrapolateDetailsFromSyntheticTxn();
            return transactionBody;
        }
        return TransactionBody.newBuilder();
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var entityAddressSequencer =
                ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getEntityAddressSequencer();
        final var mirrorEvmContractAliases =
                (MirrorEvmContractAliases) ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).aliases();
        initializeHederaTokenStore(store);
        if (impliedValidity == null) {
            extrapolateDetailsFromSyntheticTxn();
        }
        if (impliedValidity != OK) {
            throw new InvalidTransactionException(impliedValidity, StringUtils.EMPTY, StringUtils.EMPTY);
        }

        final var changes = impliedTransfers.getAllBalanceChanges();

        final Map<ByteString, EntityNum> completedLazyCreates = new HashMap<>();
        for (int i = 0, n = changes.size(); i < n; i++) {
            final var change = changes.get(i);
            if (change.hasAlias()) {
                replaceAliasWithId(
                        change, completedLazyCreates, store, entityAddressSequencer, mirrorEvmContractAliases);
            }
        }

        transferLogic.doZeroSum(changes, store, entityAddressSequencer, mirrorEvmContractAliases, hederaTokenStore);
        return new EmptyRunResult();
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(
                ABI_ID_CRYPTO_TRANSFER,
                ABI_ID_CRYPTO_TRANSFER_V2,
                ABI_ID_TRANSFER_TOKENS,
                ABI_ID_TRANSFER_TOKEN,
                ABI_ID_TRANSFER_NFTS,
                ABI_ID_TRANSFER_NFT);
    }

    private void initializeHederaTokenStore(Store store) {
        hederaTokenStore = new HederaTokenStore(contextOptionValidator, mirrorNodeEvmProperties, store);
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
            if (amount > 0 && accountID.hasAlias()) {
                accountID = generateAccountIDWithAliasCalculatedFrom(accountID);
            }

            DecodingFacade.addSignedAdjustment(fungibleTransfers, tokenType, accountID, amount, false);
        }
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime, final TransactionBody transactionBody) {
        Objects.requireNonNull(transferOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        long accumulatedCost = 0;
        // TODO: Add AssessedCustomFee logic. For now use pricing for transfer with customFees
        final boolean customFees = impliedTransfers != null;
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
        if (mirrorNodeEvmProperties.isLazyCreationEnabled() && numLazyCreates > 0) {
            final var lazyCreationFee = pricingUtils.getMinimumPriceInTinybars(GasCostType.CRYPTO_CREATE, consensusTime)
                    + pricingUtils.getMinimumPriceInTinybars(GasCostType.CRYPTO_UPDATE, consensusTime);
            accumulatedCost += numLazyCreates * lazyCreationFee;
        }
        return accumulatedCost;
    }

    private void replaceAliasWithId(
            final BalanceChange change,
            final Map<ByteString, EntityNum> completedLazyCreates,
            Store store,
            EntityAddressSequencer entityAddressSequencer,
            MirrorEvmContractAliases mirrorEvmContractAliases) {
        final var receiverAlias = change.getNonEmptyAliasIfPresent();
        if (completedLazyCreates.containsKey(receiverAlias)) {
            change.replaceNonEmptyAliasWith(completedLazyCreates.get(receiverAlias));
        } else {
            final var lazyCreateResult = autoCreationLogic.create(
                    change,
                    Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .build(),
                    store,
                    entityAddressSequencer,
                    mirrorEvmContractAliases);
            validateTrue(lazyCreateResult.getLeft() == OK, lazyCreateResult.getLeft());
            completedLazyCreates.put(
                    receiverAlias,
                    EntityNum.fromAccountId(
                            change.counterPartyAccountId() == null
                                    ? change.accountId()
                                    : change.counterPartyAccountId()));
        }
    }

    protected void extrapolateDetailsFromSyntheticTxn() {
        Objects.requireNonNull(
                transferOp, "`body` method should be called before `extrapolateDetailsFromSyntheticTxn`");

        final var op = transactionBody.getCryptoTransfer();
        impliedValidity = validityWithCurrentProps(op);
        final var explicitChanges = constructBalanceChanges();
        impliedTransfers = new ImpliedTransfers(explicitChanges);
    }

    private ResponseCodeEnum validityWithCurrentProps(CryptoTransferTransactionBody transferOp) {
        final var hbarAdjustWrapper = transferOp.getTransfers();
        final var tokenAdjustsList = transferOp.getTokenTransfersList();
        final var hbarAdjusts = hbarAdjustWrapper.getAccountAmountsList();

        if (hasRepeatedAccount(hbarAdjusts)) {
            return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
        }
        if (!isNetZeroAdjustment(hbarAdjusts)) {
            return INVALID_ACCOUNT_AMOUNTS;
        }

        return validateTokenTransferSemantics(tokenAdjustsList);
    }

    ResponseCodeEnum validateTokenTransferSemantics(List<TokenTransferList> tokenTransfersList) {
        if (tokenTransfersList.isEmpty()) {
            return OK;
        }
        ResponseCodeEnum validity;
        final Set<TokenID> uniqueTokens = new HashSet<>();
        final Set<Long> uniqueSerialNos = new HashSet<>();
        for (var tokenTransfers : tokenTransfersList) {
            validity = validateScopedTransferSemantics(uniqueTokens, tokenTransfers, uniqueSerialNos);
            if (validity != OK) {
                return validity;
            }
        }
        if (uniqueTokens.size() < tokenTransfersList.size()) {
            return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
        }
        return OK;
    }

    private ResponseCodeEnum validateScopedTransferSemantics(
            final Set<TokenID> uniqueTokens, final TokenTransferList tokenTransfers, final Set<Long> uniqueSerialNos) {
        if (!tokenTransfers.hasToken()) {
            return INVALID_TOKEN_ID;
        }
        uniqueTokens.add(tokenTransfers.getToken());
        final var ownershipChanges = tokenTransfers.getNftTransfersList();
        uniqueSerialNos.clear();
        for (final var ownershipChange : ownershipChanges) {
            if (ownershipChange.getSenderAccountID().equals(ownershipChange.getReceiverAccountID())) {
                return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
            }
            if (!uniqueSerialNos.add(ownershipChange.getSerialNumber())) {
                return INVALID_ACCOUNT_AMOUNTS;
            }
        }

        final var adjusts = tokenTransfers.getTransfersList();
        for (var adjust : adjusts) {
            if (!adjust.hasAccountID()) {
                return INVALID_ACCOUNT_ID;
            }
        }
        if (hasRepeatedAccount(adjusts)) {
            return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
        }
        if (!isNetZeroAdjustment(adjusts)) {
            return TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
        }
        return OK;
    }

    boolean isNetZeroAdjustment(List<AccountAmount> adjusts) {
        var net = ZERO;
        for (var adjust : adjusts) {
            net = net.add(BigInteger.valueOf(adjust.getAmount()));
        }
        return net.equals(ZERO);
    }

    boolean hasRepeatedAccount(List<AccountAmount> adjusts) {
        final int n = adjusts.size();
        if (n < 2) {
            return false;
        }
        Set<AccountID> allowanceAAs = new HashSet<>();
        Set<AccountID> normalAAs = new HashSet<>();
        for (var i = 0; i < n; i++) {
            final var adjust = adjusts.get(i);
            if (adjust.getIsApproval()) {
                if (!allowanceAAs.contains(adjust.getAccountID())) {
                    allowanceAAs.add(adjust.getAccountID());
                } else {
                    return true;
                }
            } else {
                if (!normalAAs.contains(adjust.getAccountID())) {
                    normalAAs.add(adjust.getAccountID());
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private List<BalanceChange> constructBalanceChanges() {
        Objects.requireNonNull(transferOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        final List<BalanceChange> allChanges = new ArrayList<>();
        final var accountId = EntityIdUtils.accountIdFromEvmAddress(senderAddress);
        Set<AccountID> requestedLazyCreates = new HashSet<>();
        for (final TokenTransferWrapper tokenTransferWrapper : transferOp.tokenTransferWrappers()) {
            for (final var fungibleTransfer : tokenTransferWrapper.fungibleTransfers()) {
                final var receiver = fungibleTransfer.receiver();
                if (fungibleTransfer.sender() != null && receiver != null) {
                    allChanges.addAll(List.of(
                            BalanceChange.changingFtUnits(
                                    Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                                    fungibleTransfer.getDenomination(),
                                    aaWith(receiver, fungibleTransfer.amount(), fungibleTransfer.isApproval()),
                                    accountId),
                            BalanceChange.changingFtUnits(
                                    Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                                    fungibleTransfer.getDenomination(),
                                    aaWith(
                                            fungibleTransfer.sender(),
                                            -fungibleTransfer.amount(),
                                            fungibleTransfer.isApproval()),
                                    accountId)));
                    if (!receiver.getAlias().isEmpty()) {
                        requestedLazyCreates.add(receiver);
                    }
                } else if (fungibleTransfer.sender() == null) {
                    allChanges.add(BalanceChange.changingFtUnits(
                            Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                            fungibleTransfer.getDenomination(),
                            aaWith(receiver, fungibleTransfer.amount(), fungibleTransfer.isApproval()),
                            accountId));
                    if (!receiver.getAlias().isEmpty()) {
                        requestedLazyCreates.add(receiver);
                    }
                } else {
                    allChanges.add(BalanceChange.changingFtUnits(
                            Id.fromGrpcToken(fungibleTransfer.getDenomination()),
                            fungibleTransfer.getDenomination(),
                            aaWith(
                                    fungibleTransfer.sender(),
                                    -fungibleTransfer.amount(),
                                    fungibleTransfer.isApproval()),
                            accountId));
                }
            }
            for (final var nftExchange : tokenTransferWrapper.nftExchanges()) {
                final var asGrpc = nftExchange.asGrpc();
                final var receiverAccountID = asGrpc.getReceiverAccountID();
                if (!receiverAccountID.getAlias().isEmpty()) {
                    requestedLazyCreates.add(receiverAccountID);
                }
                allChanges.add(BalanceChange.changingNftOwnership(
                        Id.fromGrpcToken(nftExchange.getTokenType()), nftExchange.getTokenType(), asGrpc, accountId));
            }
        }

        for (final var hbarTransfer : transferOp.transferWrapper().hbarTransfers()) {
            if (hbarTransfer.sender() != null) {
                allChanges.add(BalanceChange.changingHbar(
                        aaWith(hbarTransfer.sender(), -hbarTransfer.amount(), hbarTransfer.isApproval()), accountId));
            } else if (hbarTransfer.receiver() != null) {
                final var receiver = hbarTransfer.receiver();
                if (!receiver.getAlias().isEmpty()) {
                    requestedLazyCreates.add(receiver);
                }
                allChanges.add(BalanceChange.changingHbar(
                        aaWith(receiver, hbarTransfer.amount(), hbarTransfer.isApproval()), accountId));
            }
        }
        numLazyCreates = requestedLazyCreates.size();
        return allChanges;
    }

    private AccountAmount aaWith(final AccountID account, final long amount, final boolean isApproval) {
        return AccountAmount.newBuilder()
                .setAccountID(account)
                .setAmount(amount)
                .setIsApproval(isApproval)
                .build();
    }
}
