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
import jakarta.inject.Named;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;
import lombok.NoArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;

@NoArgsConstructor
@Named
public class FunctionEncodeDecoder {
    private static final String ADDRESS_DUO = "(address,address)";
    private static final String INT = "(int256)";
    private static final String INT64 = "(int64)";
    private static final String TRIPLE_ADDRESS = "(address,address,address)";
    private static final String ADDRESS_UINT = "(address,uint256)";
    private static final String ADDRESS_DUO_UINT = "(address,address,uint256)";
    private static final String TRIPLE_ADDRESS_UINT = "(address,address,address,uint256)";
    private static final String ADDRESS_INT64 = "(address,int64)";
    private static final String KEY_VALUE = "((bool,address,bytes,bytes,address))";
    private static final String CUSTOM_FEE = "(bytes,bytes,bytes)";

    private final Map<String, String> functionsAbi = new HashMap<>();

    public byte[] getContractBytes(final Path contractPath) {
        try {
            return Hex.decode(Files.readAllBytes(contractPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Bytes functionHashFor(final String functionName, final Path contractPath, final Object... parameters) {
        final var jsonFunction = functionsAbi.getOrDefault(functionName, getFunctionAbi(functionName, contractPath));
        Function function = Function.fromJson(jsonFunction);
        Tuple parametersBytes = encodeTupleParameters(function.getInputs().getCanonicalType(), parameters);

        return Bytes.wrap(function.encodeCall(parametersBytes).array());
    }

    public Bytes functionHashWithEmptyDataFor(final String functionName, final Path contractPath, final Object... parameters) {
        final var jsonFunction = functionsAbi.getOrDefault(functionName, getFunctionAbi(functionName, contractPath));
        Function function = Function.fromJson(jsonFunction);
        return Bytes.wrap(encodeCall(function).array());
    }

    public ByteBuffer encodeCall(Function function) {
        ByteBuffer dest = ByteBuffer.allocate(function.selector().length + 3200);
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

        Function function = Function.fromJson(jsonFunction);
        return function.decodeReturn(FastHex.decode(response.replace("0x", "")));
    }

    private Tuple encodeTupleParameters(final String tupleSig, final Object... parameters) {
        return switch (tupleSig) {
            case ADDRESS -> Tuple.of(convertAddress((Address) parameters[0]));
            case STRING, UINT8, BOOL, INT64 -> Tuple.of(parameters[0]);
            case UINT256, INT -> Tuple.of(BigInteger.valueOf((long) parameters[0]));
            case ADDRESS_DUO -> Tuple.of(
                    convertAddress((Address) parameters[0]), convertAddress((Address) parameters[1]));
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
            default -> Tuple.EMPTY;
        };
    }

    private String getFunctionAbi(final String functionName, final Path contractPath) {

        try (final var in = new FileInputStream(contractPath.toString())) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(in);

            JsonNode functionNode = StreamSupport.stream(rootNode.spliterator(), false)
                    .filter(node -> node.get("name").asText().equals(functionName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Function not found: " + functionName));

            functionsAbi.put(functionName, functionNode.toString());

            return functionNode.toString();
        } catch (IOException e) {
            return "Failed to parse";
        }
    }

    public static com.esaulpaugh.headlong.abi.Address convertAddress(final Address address) {
        return com.esaulpaugh.headlong.abi.Address.wrap(
                com.esaulpaugh.headlong.abi.Address.toChecksumAddress(address.toUnsignedBigInteger()));
    }
}
