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

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Strings;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.DeployedContract;
import com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface;
import jakarta.inject.Named;
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

    public ContractCallResponse contractsCall(
            final NodeNameEnum node,
            boolean isEstimate,
            final String from,
            final DeployedContract deployedContract,
            final SelectorInterface method,
            final String data,
            final TupleType returnTupleType) {
        if (NodeNameEnum.MIRROR.equals(node)) {
            var contractCallRequestBody = ContractCallRequest.builder()
                    .data(data)
                    .to(deployedContract.contractId().toSolidityAddress())
                    .from(from.isEmpty() ? contractClient.getClientAddress() : from)
                    .estimate(isEstimate)
                    .build();

            return mirrorClient.contractsCall(contractCallRequestBody);
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
                    return contractCallResponse;
                }

                contractCallResponse.setResult(e.getMessage());
            }

            return contractCallResponse;
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
}
