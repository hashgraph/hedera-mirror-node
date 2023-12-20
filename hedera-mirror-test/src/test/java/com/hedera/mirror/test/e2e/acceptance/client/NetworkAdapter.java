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

package com.hedera.mirror.test.e2e.acceptance.client;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.getAbiFunctionAsJsonString;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.Strings;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource;
import com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.DeployedContract;
import com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.SelectorInterface;
import jakarta.inject.Named;
import java.io.IOException;
import java.math.BigInteger;
import org.springframework.beans.factory.annotation.Autowired;

@Named
public class NetworkAdapter extends EncoderDecoderFacade {

    @Autowired
    private ContractClient contractClient;

    @Autowired
    private MirrorNodeClient mirrorClient;

    public ContractCallResponse contractsCall(
            boolean toMirror,
            boolean isEstimate,
            final String from,
            final ContractResource contractResource,
            final DeployedContract deployedContract,
            final SelectorInterface method,
            final String data) {
        if (toMirror) {
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
            final var result = contractClient.executeContractQuery(
                    deployedContract.contractId(), method.getSelector(), gas, decodedData);
            return convertResponseFromConsensusNode(contractResource, method, result.asBytes());
        }
    }

    public com.esaulpaugh.headlong.abi.Address asLongZeroHeadlongAddress(final ContractId contractID) {
        return Address.wrap(Address.toChecksumAddress(BigInteger.valueOf(contractID.num)));
    }

    public com.esaulpaugh.headlong.abi.Address asLongZeroHeadlongAddress(final AccountId accountId) {
        return Address.wrap(Address.toChecksumAddress(BigInteger.valueOf(accountId.num)));
    }

    private ContractCallResponse convertResponseFromConsensusNode(
            final ContractResource contractResource, final SelectorInterface method, final byte[] result) {
        final var decodedResult = decodeResult(contractResource, method, result);
        final var contractCallResponse = new ContractCallResponse();
        if (decodedResult.size() == 1) {
            if (decodedResult.get(0) instanceof String) {
                contractCallResponse.setResult(decodedResult.get(0));
            } else if (decodedResult.get(0) instanceof BigInteger) {
                contractCallResponse.setResult(decodedResult.get(0).toString());
            } else if (decodedResult.get(0) instanceof Boolean) {
                contractCallResponse.setResult(decodedResult.get(0).toString());
            } else if (decodedResult.get(0) instanceof byte[]) {
                contractCallResponse.setResult(decodedResult.get(0));
            } else if (decodedResult.get(0) instanceof Address) {
                contractCallResponse.setResult(((Address) decodedResult.get(0)).toString());
            }
        }

        return contractCallResponse;
    }

    private Tuple decodeResult(ContractResource resource, SelectorInterface method, final byte[] result) {
        String json;
        try (var in = getResourceAsStream(resource.getPath())) {
            json = getAbiFunctionAsJsonString(readCompiledArtifact(in), method.getSelector());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Function function = Function.fromJson(json);
        return function.decodeReturn(result);
    }
}
