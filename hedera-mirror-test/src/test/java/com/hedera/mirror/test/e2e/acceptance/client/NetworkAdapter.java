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

package com.hedera.mirror.test.e2e.acceptance.client;

import static com.hedera.mirror.rest.model.ContractAction.ResultDataTypeEnum.OUTPUT;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.hexToAscii;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Strings;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.mirror.rest.model.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import com.hedera.mirror.test.e2e.acceptance.response.GeneralContractExecutionResponse;
import com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.DeployedContract;
import com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface;
import com.hedera.mirror.test.e2e.acceptance.util.ContractCallResponseWrapper;
import com.hedera.mirror.test.e2e.acceptance.util.ModelBuilder;
import jakarta.inject.Named;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.beans.factory.annotation.Autowired;

@Named
public class NetworkAdapter extends EncoderDecoderFacade {

    @Autowired
    private ContractClient contractClient;

    @Autowired
    private MirrorNodeClient mirrorClient;

    public static final String UINT256 = "(uint256)";

    public static final String BYTES = "(bytes)";

    public static final TupleType BIG_INTEGER_TUPLE = TupleType.parse(UINT256);
    public static final TupleType BYTES_TUPLE = TupleType.parse(BYTES);

    public ContractCallResponseWrapper contractsCall(
            final NodeNameEnum node,
            boolean isEstimate,
            final String from,
            final DeployedContract deployedContract,
            final SelectorInterface method,
            final String data,
            final TupleType returnTupleType) {
        if (NodeNameEnum.MIRROR.equals(node)) {
            try {
                var contractCallRequestBody = ModelBuilder.contractCallRequest()
                        .data(data)
                        .estimate(isEstimate)
                        .from(from.isEmpty() ? contractClient.getClientAddress() : from)
                        .to(deployedContract.contractId().toSolidityAddress());

                return ContractCallResponseWrapper.of(mirrorClient.contractsCall(contractCallRequestBody));
            } catch (Exception e) {
                ContractCallResponse contractCallResponse = new ContractCallResponse();
                contractCallResponse.setResult(e.getMessage());
                return ContractCallResponseWrapper.of(contractCallResponse);
            }
        } else {
            final var gas = contractClient
                    .getSdkClient()
                    .getAcceptanceTestProperties()
                    .getFeatureProperties()
                    .getMaxContractFunctionGas();

            final var decodedData = Strings.decode(data);
            ContractCallResponse contractCallResponse;
            try {
                final var result = contractClient.executeContractQuery(
                        deployedContract.contractId(), method.getSelector(), gas, decodedData);
                contractCallResponse = convertConsensusResponse(result, returnTupleType);
            } catch (final Exception e) {
                contractCallResponse = new ContractCallResponse();
                if (e instanceof PrecheckStatusException pse) {
                    final var exceptionReason = pse.status.toString();
                    contractCallResponse.setResult(exceptionReason);
                    return ContractCallResponseWrapper.of(contractCallResponse);
                }

                contractCallResponse.setResult(e.getMessage());
            }

            return ContractCallResponseWrapper.of(contractCallResponse);
        }
    }

    public GeneralContractExecutionResponse contractsCall(
            final NodeNameEnum node,
            boolean isEstimate,
            final String from,
            final DeployedContract deployedContract,
            final String method,
            final byte[] data,
            final Hbar amount) {
        if (NodeNameEnum.MIRROR.equals(node)) {
            var contractCallRequestBody = ModelBuilder.contractCallRequest()
                    .data(Strings.encode(ByteBuffer.wrap(data)))
                    .to(deployedContract.contractId().toSolidityAddress())
                    .from(from.isEmpty() ? contractClient.getClientAddress() : from)
                    .estimate(isEstimate)
                    .value(amount != null ? amount.toTinybars() : 0);

            try {
                var response = mirrorClient.contractsCall(contractCallRequestBody);
                return new GeneralContractExecutionResponse(ContractCallResponseWrapper.of(response));
            } catch (Exception e) {
                return new GeneralContractExecutionResponse(e.getMessage());
            }
        } else {
            final var gas = contractClient
                    .getSdkClient()
                    .getAcceptanceTestProperties()
                    .getFeatureProperties()
                    .getMaxContractFunctionGas();

            try {
                final var result =
                        contractClient.executeContract(deployedContract.contractId(), gas, method, data, amount);
                final var txId = result.networkTransactionResponse().getTransactionIdStringNoCheckSum();
                final var errorMessage = extractInternalCallErrorMessage(txId);
                return new GeneralContractExecutionResponse(txId, result.networkTransactionResponse(), errorMessage);
            } catch (Exception e) {
                final var txId = extractTransactionId(e.getMessage());
                final var errorMessage = extractInternalCallErrorMessage(txId);
                return new GeneralContractExecutionResponse(txId, errorMessage);
            }
        }
    }

    private ContractCallResponse convertConsensusResponse(
            final ContractFunctionResult result, final TupleType returnTupleType) {
        final var tupleResult = result.getResult(returnTupleType.getCanonicalType());

        final var contractCallResponse = new ContractCallResponse();

        if (isNotEmpty(tupleResult) && tupleResult.get(0) instanceof byte[] bytes) {
            if (bytes.length == 0) {
                contractCallResponse.setResult(StringUtils.EMPTY);
            }
        } else {
            final var encodedResult =
                    Bytes.wrap(returnTupleType.encode(tupleResult).array());
            contractCallResponse.setResult(encodedResult.toString());
        }
        return contractCallResponse;
    }

    private String extractInternalCallErrorMessage(String transactionId) throws IllegalArgumentException {
        var transactions = mirrorClient.getTransactions(transactionId).getTransactions();
        if (transactions == null || transactions.size() < 2) {
            var actions = mirrorClient.getContractActions(transactionId).getActions();
            if (actions == null || actions.size() < 2) {
                throw new IllegalArgumentException("The actions list must contain at least two elements.");
            }
            if (actions.get(1).getResultDataType() != OUTPUT) {
                String hexString = actions.get(1).getResultData();
                return hexToAscii(hexString.replace("0x", ""));
            }
            return null;
        }

        var resultMessage = transactions.get(1).getResult();
        return resultMessage.equalsIgnoreCase("success") ? null : resultMessage;
    }

    private static String extractTransactionId(String message) {
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)@(\\d+)\\.(\\d+)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
        } else {
            return "Not found";
        }
    }
}
