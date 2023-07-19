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
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FIXED_FEE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FIXED_FEE_V2;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE_V2;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE_V2;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.FIXED_FEE_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.FRACTIONAL_FEE_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_V2;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.HEDERA_TOKEN_STRUCT_V3;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.ROYALTY_FEE_DECODER;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenExpiry;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeTokenKeys;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FixedFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FractionalFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.RoyaltyFeeWrapper;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.CreateLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TokenCreatePrecompile extends AbstractWritePrecompile {
    private static final String CREATE_FUNGIBLE_TOKEN_STRING = "createFungibleToken(";
    private static final String CREATE_NON_FUNGIBLE_TOKEN_STRING = "createNonFungibleToken(";
    private static final String CREATE_FUNGIBLE_TOKEN_WITH_FEES_STRING = "createFungibleTokenWithCustomFees(";
    private static final String CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_STRING = "createNonFungibleTokenWithCustomFees(";
    private static final String TOKEN_CREATE = String.format(FAILURE_MESSAGE, "token create");
    private static final Function TOKEN_CREATE_FUNGIBLE_FUNCTION =
            new Function(CREATE_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT + ",uint256,uint256)");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_SELECTOR = Bytes.wrap(TOKEN_CREATE_FUNGIBLE_FUNCTION.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_FUNGIBLE_DECODER =
            TypeFactory.create("(" + HEDERA_TOKEN_STRUCT_DECODER + ",uint256,uint256)");
    private static final Function TOKEN_CREATE_FUNGIBLE_FUNCTION_V2 =
            new Function(CREATE_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT_V2 + ",uint64,uint32)");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_FUNCTION_V2.selector());
    private static final Function TOKEN_CREATE_FUNGIBLE_FUNCTION_V3 =
            new Function(CREATE_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT_V3 + ",int64,int32)");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_SELECTOR_V3 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_FUNCTION_V3.selector());
    private static final Function TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION =
            new Function(CREATE_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT
                    + ",uint256,uint256,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION.selector());
    private static final Function TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V2 =
            new Function(CREATE_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT_V2
                    + ",uint64,uint32,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V2.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_FUNGIBLE_WITH_FEES_DECODER = TypeFactory.create("("
            + HEDERA_TOKEN_STRUCT_DECODER
            + ",uint256,uint256,"
            + FIXED_FEE_DECODER
            + ARRAY_BRACKETS
            + ","
            + FRACTIONAL_FEE_DECODER
            + ARRAY_BRACKETS
            + ")");

    private static final Function TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V3 =
            new Function(CREATE_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT_V3
                    + ",int64,int32,"
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE_V2
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V3 =
            Bytes.wrap(TOKEN_CREATE_FUNGIBLE_WITH_FEES_FUNCTION_V3.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_FUNCTION =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_FUNCTION.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V2 =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT_V2 + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V2.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_NON_FUNGIBLE_DECODER =
            TypeFactory.create("(" + HEDERA_TOKEN_STRUCT_DECODER + ")");
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V3 =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_STRING + HEDERA_TOKEN_STRUCT_V3 + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V3 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_FUNCTION_V3.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT
                    + ","
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION.selector());
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V2 =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT_V2
                    + ","
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V2 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V2.selector());
    public static final ABIType<Tuple> TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_DECODER = TypeFactory.create("("
            + HEDERA_TOKEN_STRUCT_DECODER
            + ","
            + FIXED_FEE_DECODER
            + ARRAY_BRACKETS
            + ","
            + ROYALTY_FEE_DECODER
            + ARRAY_BRACKETS
            + ")");
    private static final Function TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V3 =
            new Function(CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_STRING
                    + HEDERA_TOKEN_STRUCT_V3
                    + ","
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE_V2
                    + ARRAY_BRACKETS
                    + ")");
    public static final Bytes TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V3 =
            Bytes.wrap(TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_FUNCTION_V3.selector());
    private final EncodingFacade encoder;
    private final CreateLogic createLogic;
    private TokenCreateWrapper tokenCreateOp;

    public TokenCreatePrecompile(
            final PrecompilePricingUtils pricingUtils,
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final CreateLogic createLogic) {
        super(pricingUtils, syntheticTxnFactory);
        this.encoder = encoder;
        this.createLogic = createLogic;
    }

    @Override
    public Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var functionId = input.getInt(0);
        tokenCreateOp = switch (functionId) {
            case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN -> decodeFungibleCreate(input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES -> decodeFungibleCreateWithFees(
                    input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN -> decodeNonFungibleCreate(input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES -> decodeNonFungibleCreateWithFees(
                    input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2 -> decodeFungibleCreateV2(input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2 -> decodeFungibleCreateWithFeesV2(
                    input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2 -> decodeNonFungibleCreateV2(input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2 -> decodeNonFungibleCreateWithFeesV2(
                    input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V3 -> decodeFungibleCreateV3(input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3 -> decodeFungibleCreateWithFeesV3(
                    input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3 -> decodeNonFungibleCreateV3(input, aliasResolver);
            case AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3 -> decodeNonFungibleCreateWithFeesV3(
                    input, aliasResolver);
            default -> null;};

        // TODO: will be implemented in next PR
        return null;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime, TransactionBody transactionBody) {
        return 100_000L;
    }

    @Override
    public RunResult run(MessageFrame frame, TransactionBody transactionBody) {
        return null;
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of();
    }

    @Override
    public void handleSentHbars(MessageFrame frame) {
        super.handleSentHbars(frame);
    }

    @Override
    public Bytes getSuccessResultFor(RunResult runResult) {
        final var tokenCreateResult = (TokenCreateResult) runResult;
        return encoder.encodeCreateSuccess(asTypedEvmAddress(tokenCreateResult.tokenId()));
    }

    @Override
    public Bytes getFailureResultFor(ResponseCodeEnum status) {
        return encoder.encodeCreateFailure(status);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeFungibleCreateV2(). The selector for this function is derived from:
     * createFungibleToken((string,string,address,string,bool,uint32,bool,(uint256,(bool,address,bytes,bytes,address)
     * )[], (uint32,address,uint32)),uint256,uint256)
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreate(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_FUNGIBLE_SELECTOR, TOKEN_CREATE_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), true, decodedArguments.get(1), decodedArguments.get(2), aliasResolver);
    }

    public static TokenCreateWrapper decodeTokenCreateWithoutFees(
            @NonNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final BigInteger initSupply,
            final BigInteger decimals,
            final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungible(tokenCreateStruct, isFungible, initSupply, decimals, aliasResolver);
    }

    public static TokenCreateWrapper decodeTokenCreateWithoutFeesV2(
            @NonNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final BigInteger initSupply,
            final BigInteger decimals,
            final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungible(tokenCreateStruct, isFungible, initSupply, decimals, aliasResolver);
    }

    private static TokenCreateWrapper getTokenCreateWrapperFungible(
            @NonNull final Tuple tokenCreateStruct,
            final boolean isFungible,
            final BigInteger initSupply,
            final BigInteger decimals,
            final UnaryOperator<byte[]> aliasResolver) {
        final var tokenName = (String) tokenCreateStruct.get(0);
        final var tokenSymbol = (String) tokenCreateStruct.get(1);
        final var tokenTreasury = convertLeftPaddedAddressToAccountId(tokenCreateStruct.get(2), aliasResolver);
        final var memo = (String) tokenCreateStruct.get(3);
        final var isSupplyTypeFinite = (Boolean) tokenCreateStruct.get(4);
        final var maxSupply = (long) tokenCreateStruct.get(5);
        final var isFreezeDefault = (Boolean) tokenCreateStruct.get(6);
        final var tokenKeys = decodeTokenKeys(tokenCreateStruct.get(7), aliasResolver);
        final var tokenExpiry = decodeTokenExpiry(tokenCreateStruct.get(8), aliasResolver);

        return new TokenCreateWrapper(
                isFungible,
                tokenName,
                tokenSymbol,
                tokenTreasury.getAccountNum() != 0 ? tokenTreasury : null,
                memo,
                isSupplyTypeFinite,
                initSupply,
                decimals,
                maxSupply,
                isFreezeDefault,
                tokenKeys,
                tokenExpiry);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is an old version of this method and is superseded by
     * decodeFungibleCreateWithFeesV2(). The selector for this function is derived from:
     * createFungibleTokenWithCustomFees((string,string,address,string,bool,uint32,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(uint32,address,uint32)),uint256,uint256,(uint32,address,bool,bool,address)[],
     * (uint32,uint32,uint32,uint32,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateWithFees(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungibleWithFees(input, aliasResolver, TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR);
    }

    private static TokenCreateWrapper getTokenCreateWrapperFungibleWithFees(
            final Bytes input,
            final UnaryOperator<byte[]> aliasResolver,
            final Bytes tokenCreateFungibleWithFeesSelector) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, tokenCreateFungibleWithFeesSelector, TOKEN_CREATE_FUNGIBLE_WITH_FEES_DECODER);

        final var tokenCreateWrapper = decodeTokenCreateWithoutFees(
                decodedArguments.get(0), true, decodedArguments.get(1), decodedArguments.get(2), aliasResolver);
        final var fixedFees = decodeFixedFees(decodedArguments.get(3), aliasResolver);
        final var fractionalFees = decodeFractionalFees(decodedArguments.get(4), aliasResolver);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setFractionalFees(fractionalFees);

        return tokenCreateWrapper;
    }

    public static List<FixedFeeWrapper> decodeFixedFees(
            @NonNull final Tuple[] fixedFeesTuples, final UnaryOperator<byte[]> aliasResolver) {
        final List<FixedFeeWrapper> fixedFees = new ArrayList<>(fixedFeesTuples.length);
        for (final var fixedFeeTuple : fixedFeesTuples) {
            final var amount = (long) fixedFeeTuple.get(0);
            final var tokenId = convertAddressBytesToTokenID(fixedFeeTuple.get(1));
            final var useHbarsForPayment = (Boolean) fixedFeeTuple.get(2);
            final var useCurrentTokenForPayment = (Boolean) fixedFeeTuple.get(3);
            final var feeCollector = convertLeftPaddedAddressToAccountId(fixedFeeTuple.get(4), aliasResolver);
            fixedFees.add(new FixedFeeWrapper(
                    amount,
                    tokenId.getTokenNum() != 0 ? tokenId : null,
                    useHbarsForPayment,
                    useCurrentTokenForPayment,
                    feeCollector.getAccountNum() != 0 ? feeCollector : null));
        }
        return fixedFees;
    }

    public static List<FractionalFeeWrapper> decodeFractionalFees(
            @NonNull final Tuple[] fractionalFeesTuples, final UnaryOperator<byte[]> aliasResolver) {
        final List<FractionalFeeWrapper> fractionalFees = new ArrayList<>(fractionalFeesTuples.length);
        for (final var fractionalFeeTuple : fractionalFeesTuples) {
            final var numerator = (long) fractionalFeeTuple.get(0);
            final var denominator = (long) fractionalFeeTuple.get(1);
            final var minimumAmount = (long) fractionalFeeTuple.get(2);
            final var maximumAmount = (long) fractionalFeeTuple.get(3);
            final var netOfTransfers = (Boolean) fractionalFeeTuple.get(4);
            final var feeCollector = convertLeftPaddedAddressToAccountId(fractionalFeeTuple.get(5), aliasResolver);
            fractionalFees.add(new FractionalFeeWrapper(
                    numerator,
                    denominator,
                    minimumAmount,
                    maximumAmount,
                    netOfTransfers,
                    feeCollector.getAccountNum() != 0 ? feeCollector : null));
        }
        return fractionalFees;
    }

    public static TokenCreateWrapper decodeNonFungibleCreate(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_NON_FUNGIBLE_SELECTOR, TOKEN_CREATE_NON_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
    }

    public static TokenCreateWrapper decodeNonFungibleCreateWithFees(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapper(input, aliasResolver, TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR);
    }

    public static List<RoyaltyFeeWrapper> decodeRoyaltyFees(
            @NonNull final Tuple[] royaltyFeesTuples, final UnaryOperator<byte[]> aliasResolver) {
        final List<RoyaltyFeeWrapper> decodedRoyaltyFees = new ArrayList<>(royaltyFeesTuples.length);
        for (final var royaltyFeeTuple : royaltyFeesTuples) {
            final var numerator = (long) royaltyFeeTuple.get(0);
            final var denominator = (long) royaltyFeeTuple.get(1);

            // When at least 1 of the following 3 values is different from its default value,
            // we treat it as though the user has tried to specify a fallbackFixedFee
            final var fixedFeeAmount = (long) royaltyFeeTuple.get(2);
            final var fixedFeeTokenId = convertAddressBytesToTokenID(royaltyFeeTuple.get(3));
            final var fixedFeeUseHbars = (Boolean) royaltyFeeTuple.get(4);
            FixedFeeWrapper fixedFee = null;
            if (fixedFeeAmount != 0 || fixedFeeTokenId.getTokenNum() != 0 || Boolean.TRUE.equals(fixedFeeUseHbars)) {
                fixedFee = new FixedFeeWrapper(
                        fixedFeeAmount,
                        fixedFeeTokenId.getTokenNum() != 0 ? fixedFeeTokenId : null,
                        fixedFeeUseHbars,
                        false,
                        null);
            }

            final var feeCollector = convertLeftPaddedAddressToAccountId(royaltyFeeTuple.get(5), aliasResolver);
            decodedRoyaltyFees.add(new RoyaltyFeeWrapper(
                    numerator, denominator, fixedFee, feeCollector.getAccountNum() != 0 ? feeCollector : null));
        }
        return decodedRoyaltyFees;
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes decodeFungibleCreate(). The
     * selector for this function is derived from:
     * createFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],
     * (uint32,address,uint32)),uint64,uint32)
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_FUNGIBLE_SELECTOR_V2, TOKEN_CREATE_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), true, decodedArguments.get(1), decodedArguments.get(2), aliasResolver);
    }

    public static TokenCreateWrapper decodeFungibleCreateV3(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_FUNGIBLE_SELECTOR_V3, TOKEN_CREATE_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFeesV2(
                decodedArguments.get(0), true, decodedArguments.get(1), decodedArguments.get(2), aliasResolver);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is an old version and is superseded by
     * decodeFungibleCreateWithFeesV3(). The selector for this function is derived from:
     * createFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(uint32,address,uint32)),uint64,uint32,(uint32,address,bool,bool,address)[],
     * (uint32,uint32,uint32,uint32,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateWithFeesV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungibleWithFees(input, aliasResolver, TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V2);
    }

    /**
     * Decodes the given bytes of the fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes
     * decodeFungibleCreateWithFeesV2(). The selector for this function is derived from:
     * createFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(int64,address,int64)),int64,int32,(int64,address,bool,bool,address)[],
     * (int64,int64,int64,int64,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeFungibleCreateWithFeesV3(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapperFungibleWithFees(input, aliasResolver, TOKEN_CREATE_FUNGIBLE_WITH_FEES_SELECTOR_V3);
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes decodeNonFungibleCreateV2().
     * The selector for this function is derived from:
     * createNonFungibleToken((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,
     * address))[], (uint32,address,uint32)))
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeNonFungibleCreateV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V2, TOKEN_CREATE_NON_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
    }

    public static TokenCreateWrapper decodeNonFungibleCreateV3(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, TOKEN_CREATE_NON_FUNGIBLE_SELECTOR_V3, TOKEN_CREATE_NON_FUNGIBLE_DECODER);

        return decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is and old version and is superseded by
     * decodeNonFungibleCreateWithFeesV3(). The selector for this function is derived from:
     * createNonFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(uint32,address,uint32)),(uint32,address,bool,bool,address)[],
     * (uint32,uint32,uint32,address,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeNonFungibleCreateWithFeesV2(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapper(input, aliasResolver, TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V2);
    }

    /**
     * Decodes the given bytes of the non-fungible token.
     *
     * <p><b>Important: </b>This is the latest version and supersedes
     * decodeNonFungibleCreateWithFeesV2(). The selector for this function is derived from:
     * createNonFungibleTokenWithCustomFees((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,
     * bytes,address))[],(int64,address,int64)),(int64,address,bool,bool,address)[],
     * (int64,int64,int64,address,bool,address)[])
     *
     * @param input         encoded bytes containing selector and input parameters
     * @param aliasResolver function used to resolve aliases
     * @return TokenCreateWrapper codec
     */
    public static TokenCreateWrapper decodeNonFungibleCreateWithFeesV3(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        return getTokenCreateWrapper(input, aliasResolver, TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_SELECTOR_V3);
    }

    private static TokenCreateWrapper getTokenCreateWrapper(
            final Bytes input,
            final UnaryOperator<byte[]> aliasResolver,
            final Bytes tokenCreateNonFungibleWithFeesSelector) {
        final Tuple decodedArguments = decodeFunctionCall(
                input, tokenCreateNonFungibleWithFeesSelector, TOKEN_CREATE_NON_FUNGIBLE_WITH_FEES_DECODER);

        final var tokenCreateWrapper = decodeTokenCreateWithoutFees(
                decodedArguments.get(0), false, BigInteger.ZERO, BigInteger.ZERO, aliasResolver);
        final var fixedFees = decodeFixedFees(decodedArguments.get(1), aliasResolver);
        final var royaltyFees = decodeRoyaltyFees(decodedArguments.get(2), aliasResolver);
        tokenCreateWrapper.setFixedFees(fixedFees);
        tokenCreateWrapper.setRoyaltyFees(royaltyFees);

        return tokenCreateWrapper;
    }
}
