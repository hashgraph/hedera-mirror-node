/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.utils;

import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.ADDRESS;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BOOL;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.STRING;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.UINT256;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.UINT8;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.FastHex;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FixedFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.FractionalFeeWrapper;
import com.hedera.services.store.contracts.precompile.TokenCreateWrapper.RoyaltyFeeWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import jakarta.inject.Named;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;
import lombok.NoArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor
@Named
public class FunctionEncodeDecoder {
    private static final String ADDRESS_DUO = "(address,address)";
    private static final String ADDRESS_DUO_BOOL = "(address,address,bool)";
    private static final String INT = "(int256)";
    private static final String INT64 = "(int64)";
    private static final String TRIPLE_ADDRESS = "(address,address,address)";
    private static final String ADDRESS_UINT = "(address,uint256)";
    private static final String ADDRESS_DUO_UINT = "(address,address,uint256)";
    private static final String TRIPLE_ADDRESS_UINT = "(address,address,address,uint256)";
    public static final String TRIPLE_ADDRESS_INT64S = "(address,address[],address[],int64[])";
    private static final String ADDRESS_INT64 = "(address,int64)";
    private static final String KEY_VALUE = "((bool,address,bytes,bytes,address))";
    private static final String CUSTOM_FEE = "(bytes,bytes,bytes)";
    private static final String ADDRESS_ARRAY_OF_ADDRESSES = "(address,address[])";
    private static final String ADDRESS_INT_INTS = "(address,int64,int64[])";
    private static final String ADDRESS_INT_BYTES = "(address,int64,bytes[])";
    private static final String ADDRESS_INT_BYTES_ADDRESS = "(address,int64,bytes[],address)";
    private static final String ADDRESS_INT_INT64ARRAY_ADDRESS = "(address,int64,int64[],address)";
    private static final String DOUBLE_ADDRESS_INT64 = "(address,address,int64)";
    private static final String DOUBLE_ADDRESS_INT64S = "(address,address,int64[])";
    private static final String TOKEN_INT64_INT32 =
            "((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),int64,int32)";
    private static final String TOKEN =
            "((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)))";
    private static final String TOKEN_INT64_INT32_FIXED_FEE_FRACTIONAL_FEE =
            "((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),int64,int32,(int64,address,bool,bool,address)[],(int64,int64,int64,int64,bool,address)[])";
    private static final String TOKEN_FIXED_FEE_FRACTIONAL_FEE =
            "((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),(int64,address,bool,bool,address)[],(int64,int64,int64,address,bool,address)[])";
    private static final String ADDRESS_ARRAY_OF_ADDRESSES_ARRAY_OF_INT64 = "(address,address[],int64[])";
    private static final String UINT32_ADDRESS_UINT32 = "((uint32,address,uint32))";
    public static final String ADDRESS_ADDRESS_ADDRESS_INT64 = "(address,address,address,int64)";
    public static final String ADDRESS_ADDRESS_ADDRESS_UINT256_UINT256 = "(address,address,address,uint256,uint256)";
    public static final String ADDRESS_ADDRESS_UINT256_UINT256 = "(address,address,uint256,uint256)";
    private static final String ADDRESS_TOKEN =
            "(address,(string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)))";
    private static final String ADDRESS_EXPIRY = "(address,(int64,address,int64))";
    public static final String TRANSFER_LIST_TOKEN_TRANSFER_LIST =
            "(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[])";
    private static final String ADDRESS_EXPIRY_V1 = "(address,(uint32,address,uint32))";
    private static final String EXPIRY = "(int64,address,int64)";
    private static final String EXPIRY_V1 = "(uint32,address,uint32)";
    public static final String ADDRESS_ARRAY_OF_KEYS = "(address,(uint256,(bool,address,bytes,bytes,address))[])";
    public static final String ADDRESS_ARRAY_OF_KEYS_KEY_TYPE =
            "(address,(uint256,(bool,address,bytes,bytes,address))[],uint256)";
    private static final String TRIPLE_BOOL = "(bool,bool,bool)";
    private static final String INT256_INT64_INT64_ARRAY = "(int256,int64,int64[])";
    private static final String INT256_INT64 = "(int256,int64)";
    private static final String INT256_ADDRESS = "(int256,address)";
    private static final String FIXED_FEE_FRACTIONAL_FEE_ROYALTY_FEE =
            "((int64,address,bool,bool,address)[],(int64,int64,int64,int64,bool,address)[],(int64,int64,int64,address,bool,address)[])";
    private static final String TOKEN_INFO =
            "(((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),int64,bool,bool,bool,(int64,address,bool,bool,address)[],(int64,int64,int64,int64,bool,address)[],(int64,int64,int64,address,bool,address)[],string))";
    private static final String FUNGIBLE_TOKEN_INFO =
            "((((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),int64,bool,bool,bool,(int64,address,bool,bool,address)[],(int64,int64,int64,int64,bool,address)[],(int64,int64,int64,address,bool,address)[],string),int32))";
    private static final String NFT_TOKEN_INFO =
            "((((string,string,address,string,bool,int64,bool,(uint256,(bool,address,bytes,bytes,address))[],(int64,address,int64)),int64,bool,bool,bool,(int64,address,bool,bool,address)[],(int64,int64,int64,int64,bool,address)[],(int64,int64,int64,address,bool,address)[],string),int64,address,int64,bytes,address))";
    private static final String BYTES32 = "(bytes32)";
    private final Map<String, String> functionsAbi = new HashMap<>();

    public static com.esaulpaugh.headlong.abi.Address convertAddress(final Address address) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(address.toUnsignedBigInteger()));
    }

    public byte[] getContractBytes(final Path contractPath) {
        try {
            return Hex.decode(Files.readAllBytes(contractPath));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Bytes functionHashFor(final String functionName, final Path contractPath, final Object... parameters) {
        final var jsonFunction = functionsAbi.getOrDefault(functionName, getFunctionAbi(functionName, contractPath));
        final Function function = Function.fromJson(jsonFunction);
        final Tuple parametersBytes = encodeTupleParameters(function.getInputs().getCanonicalType(), parameters);

        return Bytes.wrap(function.encodeCall(parametersBytes).array());
    }

    public Bytes functionHashWithEmptyDataFor(
            final String functionName, final Path contractPath, final Object... parameters) {
        final var jsonFunction = functionsAbi.getOrDefault(functionName, getFunctionAbi(functionName, contractPath));
        final Function function = Function.fromJson(jsonFunction);
        return Bytes.wrap(encodeCall(function).array());
    }

    public ByteBuffer encodeCall(final Function function) {
        final ByteBuffer dest = ByteBuffer.allocate(function.selector().length + 3200);
        dest.put(function.selector());
        return dest;
    }

    public String encodedResultFor(final String functionName, final Path contractPath, final Object... results) {
        final var jsonFunction = functionsAbi.getOrDefault(functionName, getFunctionAbi(functionName, contractPath));
        final var func = Function.fromJson(jsonFunction);
        final var tupleType = func.getOutputs();

        return Bytes.wrap(tupleType
                        .encode(encodeTupleParameters(tupleType.toString(), results))
                        .array())
                .toHexString();
    }

    public Tuple decodeResult(final String functionName, final Path contractPath, final String response) {
        final var jsonFunction = functionsAbi.getOrDefault(functionName, getFunctionAbi(functionName, contractPath));

        final Function function = Function.fromJson(jsonFunction);
        return function.decodeReturn(FastHex.decode(response.replace("0x", "")));
    }

    private Tuple encodeTupleParameters(final String tupleSig, final Object... parameters) {
        return switch (tupleSig) {
            case ADDRESS -> Tuple.of(convertAddress((Address) parameters[0]));
            case STRING, UINT8, BOOL, INT64, BYTES32 -> Tuple.of(parameters[0]);
            case UINT256, INT -> Tuple.of(BigInteger.valueOf((long) parameters[0]));
            case ADDRESS_DUO -> Tuple.of(
                    convertAddress((Address) parameters[0]), convertAddress((Address) parameters[1]));
            case ADDRESS_DUO_BOOL, DOUBLE_ADDRESS_INT64, DOUBLE_ADDRESS_INT64S -> Tuple.of(
                    convertAddress((Address) parameters[0]), convertAddress((Address) parameters[1]), parameters[2]);
            case TRIPLE_ADDRESS -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    convertAddress((Address) parameters[1]),
                    convertAddress((Address) parameters[2]));
            case ADDRESS_DUO_UINT -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    convertAddress((Address) parameters[1]),
                    BigInteger.valueOf((long) parameters[2]));
            case TRIPLE_ADDRESS_UINT -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    convertAddress((Address) parameters[1]),
                    convertAddress((Address) parameters[2]),
                    BigInteger.valueOf((long) parameters[3]));
            case ADDRESS_UINT -> Tuple.of(
                    convertAddress((Address) parameters[0]), BigInteger.valueOf((long) parameters[1]));
            case KEY_VALUE -> Tuple.of(Tuple.of(
                    parameters[0],
                    convertAddress((Address) parameters[1]),
                    parameters[2],
                    parameters[3],
                    convertAddress((Address) parameters[4])));
            case CUSTOM_FEE -> Tuple.of(parameters[0], parameters[1], parameters[2]);
            case ADDRESS_INT64 -> Tuple.of(convertAddress((Address) parameters[0]), parameters[1]);
            case ADDRESS_ARRAY_OF_ADDRESSES -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    Arrays.stream(((Address[]) parameters[1]))
                            .map(FunctionEncodeDecoder::convertAddress)
                            .toList()
                            .toArray(new com.esaulpaugh.headlong.abi.Address[((Address[]) parameters[1]).length]));
            case ADDRESS_INT_BYTES, ADDRESS_INT_INTS -> Tuple.of(
                    convertAddress((Address) parameters[0]), parameters[1], parameters[2]);
            case ADDRESS_TOKEN -> Tuple.of(
                    convertAddress((Address) parameters[0]), encodeToken((TokenCreateWrapper) parameters[1]));
            case TOKEN_INT64_INT32 -> Tuple.of(
                    encodeToken((TokenCreateWrapper) parameters[0]), parameters[1], parameters[2]);
            case TOKEN -> Tuple.of(encodeToken((TokenCreateWrapper) parameters[0]));
            case TOKEN_INT64_INT32_FIXED_FEE_FRACTIONAL_FEE -> Tuple.of(
                    encodeToken((TokenCreateWrapper) parameters[0]),
                    parameters[1],
                    parameters[2],
                    encodeFixedFee((FixedFeeWrapper) parameters[3]),
                    encodeFractionalFee((FractionalFeeWrapper) parameters[4]));
            case TOKEN_FIXED_FEE_FRACTIONAL_FEE -> Tuple.of(
                    encodeToken((TokenCreateWrapper) parameters[0]),
                    encodeFixedFee((FixedFeeWrapper) parameters[1]),
                    encodeRoyaltyFee((RoyaltyFeeWrapper) parameters[2]));
            case ADDRESS_EXPIRY, ADDRESS_EXPIRY_V1 -> Tuple.of(
                    convertAddress((Address) parameters[0]), encodeTokenExpiry((TokenExpiryWrapper) parameters[1]));
            case EXPIRY, EXPIRY_V1 -> Tuple.of(parameters[0], convertAddress((Address) parameters[1]), parameters[2]);
            case TRIPLE_ADDRESS_INT64S -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    Arrays.stream(((Address[]) parameters[1]))
                            .map(FunctionEncodeDecoder::convertAddress)
                            .toList()
                            .toArray(new com.esaulpaugh.headlong.abi.Address[((Address[]) parameters[1]).length]),
                    Arrays.stream(((Address[]) parameters[2]))
                            .map(FunctionEncodeDecoder::convertAddress)
                            .toList()
                            .toArray(new com.esaulpaugh.headlong.abi.Address[((Address[]) parameters[2]).length]),
                    parameters[3]);
            case ADDRESS_ADDRESS_ADDRESS_INT64 -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    convertAddress((Address) parameters[1]),
                    convertAddress((Address) parameters[2]),
                    parameters[3]);
            case ADDRESS_ARRAY_OF_ADDRESSES_ARRAY_OF_INT64 -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    Arrays.stream(((Address[]) parameters[1]))
                            .map(FunctionEncodeDecoder::convertAddress)
                            .toList()
                            .toArray(new com.esaulpaugh.headlong.abi.Address[((Address[]) parameters[1]).length]),
                    parameters[2]);
            case UINT32_ADDRESS_UINT32 -> Tuple.of(
                    Tuple.of(parameters[0], convertAddress((Address) parameters[1]), parameters[2]));
            case ADDRESS_INT_BYTES_ADDRESS, ADDRESS_INT_INT64ARRAY_ADDRESS -> Tuple.of(
                    convertAddress((Address) parameters[0]), parameters[1], parameters[2], convertAddress((Address)
                            parameters[3]));
            case ADDRESS_ADDRESS_ADDRESS_UINT256_UINT256 -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    convertAddress((Address) parameters[1]),
                    convertAddress((Address) parameters[2]),
                    parameters[3],
                    parameters[4]);
            case ADDRESS_ADDRESS_UINT256_UINT256 -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    convertAddress((Address) parameters[1]),
                    parameters[2],
                    parameters[3]);
            case TRANSFER_LIST_TOKEN_TRANSFER_LIST -> Tuple.of(
                    encodeCryptoTransfer((Object[]) parameters[0]),
                    encodeCryptoTokenTransfer((Object[]) parameters[1]));
            case ADDRESS_ARRAY_OF_KEYS -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    Arrays.stream((Object[]) parameters[1])
                            .map(e -> (Object[]) e)
                            .map(e -> {
                                final var keyParams = ((Object[]) e[1]);
                                return Tuple.of(
                                        BigInteger.valueOf((int) e[0]),
                                        Tuple.of(
                                                keyParams[0],
                                                convertAddress((Address) keyParams[1]),
                                                keyParams[2],
                                                keyParams[3],
                                                convertAddress((Address) keyParams[4])));
                            })
                            .toList()
                            .toArray(new Tuple[((Object[]) parameters[1]).length]));
            case ADDRESS_ARRAY_OF_KEYS_KEY_TYPE -> Tuple.of(
                    convertAddress((Address) parameters[0]),
                    Arrays.stream((Object[]) parameters[1])
                            .map(e -> (Object[]) e)
                            .map(e -> {
                                final var keyParams = ((Object[]) e[1]);
                                return Tuple.of(
                                        BigInteger.valueOf((int) e[0]),
                                        Tuple.of(
                                                keyParams[0],
                                                convertAddress((Address) keyParams[1]),
                                                keyParams[2],
                                                keyParams[3],
                                                convertAddress((Address) keyParams[4])));
                            })
                            .toList()
                            .toArray(new Tuple[((Object[]) parameters[1]).length]),
                    BigInteger.valueOf((long) parameters[2]));
            case TRIPLE_BOOL, INT256_INT64_INT64_ARRAY -> Tuple.of(parameters[0], parameters[1], parameters[2]);
            case INT256_INT64 -> Tuple.of(parameters[0], parameters[1]);
            case INT256_ADDRESS -> Tuple.of(parameters[0], convertAddress((Address) parameters[1]));
            case FIXED_FEE_FRACTIONAL_FEE_ROYALTY_FEE -> Tuple.of(
                    encodeFixedFee((Object[]) parameters[0]),
                    encodeFractionalFee((Object[]) parameters[1]),
                    encodeRoyaltyFee((Object[]) parameters[2]));
            case TOKEN_INFO -> Tuple.of(encodeTokenInfo(parameters));
            case FUNGIBLE_TOKEN_INFO -> Tuple.of(Tuple.of(encodeTokenInfo((Object[]) parameters[0]), parameters[1]));
            case NFT_TOKEN_INFO -> Tuple.of(encodeNftTokenInfo(parameters));

            default -> Tuple.EMPTY;
        };
    }

    private Tuple encodeNftTokenInfo(Object[] parameters) {
        return Tuple.of(
                encodeTokenInfo((Object[]) parameters[0]),
                parameters[1],
                convertAddress((Address) parameters[2]),
                parameters[3],
                parameters[4],
                convertAddress((Address) parameters[5]));
    }

    @NotNull
    private Tuple encodeTokenInfo(Object[] parameters) {
        return Tuple.of(
                encodeToken((TokenCreateWrapper) parameters[0]),
                parameters[1],
                parameters[2],
                parameters[3],
                parameters[4],
                new Tuple[0],
                encodeFractionalFee((Object[]) parameters[5]),
                new Tuple[0],
                parameters[6]);
    }

    private Tuple encodeCryptoTransfer(Object[] parameters) {
        if (parameters.length == 0) {
            return Tuple.of((Object) new Tuple[0]);
        }
        return Tuple.of((Object) new Tuple[] {
            Tuple.of(convertAddress((Address) parameters[0]), -(Long) parameters[2], false),
            Tuple.of(convertAddress((Address) parameters[1]), parameters[2], false)
        });
    }

    private Tuple[] encodeCryptoTokenTransfer(Object[] parameters) {
        if (parameters.length == 0) {
            return new Tuple[] {};
        }
        if ((Boolean) parameters[4]) { // means it's a NFT transfer
            return new Tuple[] {
                Tuple.of(
                        // the address of the token
                        convertAddress((Address) parameters[0]),
                        // the list of fungible transfers
                        new Tuple[] {}
                        // the list of nft transfers
                        ,
                        new Tuple[] {
                            Tuple.of(
                                    // sender address
                                    convertAddress((Address) parameters[1]),
                                    // receiver address
                                    convertAddress((Address) parameters[2]),
                                    // nft id
                                    parameters[3],
                                    false)
                        })
            };
        } else {
            return new Tuple[] {
                Tuple.of(
                        // the address of the token
                        convertAddress((Address) parameters[0]),
                        // the list of fungible transfers
                        // parameters: accountId, amount, isApproval
                        // when the amount is positive - the address is the receiver (we add amount to the balance)
                        // when the amount is negative- the address is the sender(we deduct amount from the balance)
                        new Tuple[] {
                            Tuple.of(convertAddress((Address) parameters[1]), -(Long) parameters[3], false),
                            Tuple.of(convertAddress((Address) parameters[2]), parameters[3], false)
                        },
                        // the list of nft transfers
                        new Tuple[] {})
            };
        }
    }

    private String getFunctionAbi(final String functionName, final Path contractPath) {

        try (final var in = new FileInputStream(contractPath.toString())) {
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode rootNode = mapper.readTree(in);

            final JsonNode functionNode = StreamSupport.stream(rootNode.spliterator(), false)
                    .filter(node ->
                            node.get("name") != null // in some ABIs the constructors do not have a name property
                                    && node.get("name").asText().equals(functionName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Function not found: " + functionName));

            functionsAbi.put(functionName, functionNode.toString());

            return functionNode.toString();
        } catch (final IOException e) {
            return "Failed to parse";
        }
    }

    private Tuple encodeToken(final TokenCreateWrapper tokenCreateWrapper) {
        return Tuple.of(
                tokenCreateWrapper.getName(),
                tokenCreateWrapper.getSymbol(),
                tokenCreateWrapper.getTreasury() != null
                        ? convertAddress(EntityIdUtils.asTypedEvmAddress(tokenCreateWrapper.getTreasury()))
                        : convertAddress(Address.ZERO),
                tokenCreateWrapper.getMemo(),
                tokenCreateWrapper.isSupplyTypeFinite(),
                tokenCreateWrapper.getMaxSupply(),
                tokenCreateWrapper.isFreezeDefault(),
                tokenCreateWrapper.getTokenKeys().stream()
                        .map(tokenKeyWrapper -> Tuple.of(
                                BigInteger.valueOf(tokenKeyWrapper.keyType()),
                                Tuple.of(
                                        tokenKeyWrapper.key().isShouldInheritAccountKeySet(),
                                        tokenKeyWrapper.key().getContractID() != null
                                                ? convertAddress(EntityIdUtils.asTypedEvmAddress(
                                                        tokenKeyWrapper.key().getContractID()))
                                                : convertAddress(EntityIdUtils.asTypedEvmAddress(
                                                        ContractID.getDefaultInstance())),
                                        tokenKeyWrapper.key().getEd25519Key(),
                                        tokenKeyWrapper.key().getEcdsaSecp256k1(),
                                        tokenKeyWrapper.key().getDelegatableContractID() != null
                                                ? convertAddress(EntityIdUtils.asTypedEvmAddress(
                                                        tokenKeyWrapper.key().getDelegatableContractID()))
                                                : convertAddress(EntityIdUtils.asTypedEvmAddress(
                                                        ContractID.getDefaultInstance())))))
                        .toList()
                        .toArray(new Tuple[tokenCreateWrapper.getTokenKeys().size()]),
                Tuple.of(
                        tokenCreateWrapper.getExpiry().second(),
                        convertAddress(EntityIdUtils.asTypedEvmAddress(
                                tokenCreateWrapper.getExpiry().autoRenewAccount())),
                        tokenCreateWrapper.getExpiry().autoRenewPeriod()));
    }

    private Tuple[] encodeFixedFee(FixedFeeWrapper fixedFeeWrapper) {
        final var customFee = fixedFeeWrapper.asGrpc();
        return new Tuple[] {
            Tuple.of(
                    customFee.getFixedFee().getAmount(),
                    convertAddress(EntityIdUtils.asTypedEvmAddress(
                            customFee.getFixedFee().getDenominatingTokenId())),
                    false,
                    false,
                    convertAddress(EntityIdUtils.asTypedEvmAddress(customFee.getFeeCollectorAccountId())))
        };
    }

    private Tuple[] encodeFixedFee(Object[] fixedFee) {
        return fixedFee.length > 0
                ? new Tuple[] {
                    Tuple.of(fixedFee[0], convertAddress((Address) fixedFee[1]), false, false, convertAddress((Address)
                            fixedFee[4]))
                }
                : new Tuple[0];
    }

    private Tuple[] encodeFractionalFee(FractionalFeeWrapper fractionalFeeWrapper) {
        return new Tuple[] {
            Tuple.of(
                    fractionalFeeWrapper.numerator(),
                    fractionalFeeWrapper.denominator(),
                    fractionalFeeWrapper.minimumAmount(),
                    fractionalFeeWrapper.maximumAmount(),
                    fractionalFeeWrapper.netOfTransfers(),
                    convertAddress(
                            fractionalFeeWrapper.feeCollector() != null
                                    ? EntityIdUtils.asTypedEvmAddress(fractionalFeeWrapper.feeCollector())
                                    : Address.ZERO))
        };
    }

    private Tuple[] encodeFractionalFee(Object[] fractionalFee) {
        return fractionalFee.length > 0
                ? new Tuple[] {
                    Tuple.of(
                            fractionalFee[0],
                            fractionalFee[1],
                            fractionalFee[2],
                            fractionalFee[3],
                            fractionalFee[4],
                            convertAddress(fractionalFee[5] != null ? (Address) fractionalFee[5] : Address.ZERO))
                }
                : new Tuple[0];
    }

    private Tuple[] encodeRoyaltyFee(RoyaltyFeeWrapper royaltyFeeWrapper) {
        return new Tuple[] {
            Tuple.of(
                    royaltyFeeWrapper.numerator(),
                    royaltyFeeWrapper.denominator(),
                    royaltyFeeWrapper.asGrpc().getRoyaltyFee().getFallbackFee().getAmount(),
                    convertAddress(EntityIdUtils.asTypedEvmAddress(royaltyFeeWrapper
                            .asGrpc()
                            .getRoyaltyFee()
                            .getFallbackFee()
                            .getDenominatingTokenId())),
                    false,
                    convertAddress(
                            royaltyFeeWrapper.feeCollector() != null
                                    ? EntityIdUtils.asTypedEvmAddress(royaltyFeeWrapper.feeCollector())
                                    : Address.ZERO))
        };
    }

    private Tuple[] encodeRoyaltyFee(Object[] royaltyFee) {
        return royaltyFee.length > 0
                ? new Tuple[] {
                    Tuple.of(
                            royaltyFee[0],
                            royaltyFee[1],
                            royaltyFee[2],
                            convertAddress((Address) royaltyFee[3]),
                            false,
                            convertAddress(royaltyFee[4] != null ? (Address) royaltyFee[4] : Address.ZERO))
                }
                : new Tuple[0];
    }

    private Tuple encodeTokenExpiry(TokenExpiryWrapper tokenExpiryWrapper) {
        return Tuple.of(
                tokenExpiryWrapper.second(),
                convertAddress(EntityIdUtils.asTypedEvmAddress(tokenExpiryWrapper.autoRenewAccount())),
                tokenExpiryWrapper.autoRenewPeriod());
    }
}
