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

package com.hedera.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.rest.model.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.util.ModelBuilder;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;

abstract class AbstractEstimateFeature extends AbstractFeature {

    protected int lowerDeviation;
    protected int upperDeviation;

    @Autowired
    protected MirrorNodeClient mirrorClient;

    /**
     * Checks if the estimatedGas is within the specified range of the actualGas.
     * <p>
     * The method calculates the lower and upper bounds as percentages of the actualUsedGas, then checks if the
     * estimatedGas is within the range (inclusive).
     *
     * @param actualUsedGas     the integer value that represents the actualGas used value
     * @param estimatedGas      the integer value to be checked
     * @param lowerBoundPercent the integer percentage value for the lower bound of the acceptable range
     * @param upperBoundPercent the integer percentage value for the upper bound of the acceptable range
     */
    protected static void assertWithinDeviation(
            int actualUsedGas, int estimatedGas, int lowerBoundPercent, int upperBoundPercent) {
        int lowerDeviation = actualUsedGas * lowerBoundPercent / 100;
        int upperDeviation = actualUsedGas * upperBoundPercent / 100;

        int lowerBound = actualUsedGas + lowerDeviation;
        int upperBound = actualUsedGas + upperDeviation;

        assertThat(estimatedGas).as("Estimated gas is above lower bound").isGreaterThanOrEqualTo(lowerBound);
        assertThat(estimatedGas).as("Estimated gas is below upper bound").isLessThanOrEqualTo(upperBound);
    }

    /**
     * Validates the gas estimation for a specific contract call.
     * <p>
     * This method estimates the gas cost for a given contract call, and then checks whether the actual gas used falls
     * within an acceptable deviation range. It utilizes the provided call endpoint to perform the contract call and
     * then compares the estimated gas with the actual gas used.
     *
     * @param data            The function signature and method data of the contract call.
     * @param actualGasUsed   The actual gas amount that was used for the call.
     * @param solidityAddress The address of the solidity contract.
     * @param sender          The sender's address (optional).
     * @throws AssertionError If the actual gas used is not within the acceptable deviation range.
     */
    protected void validateGasEstimation(
            String data, ContractMethodInterface actualGasUsed, String solidityAddress, Optional<String> sender) {
        var contractCallRequest =
                ModelBuilder.contractCallRequest().data(data).estimate(true).to(solidityAddress);
        sender.ifPresent(contractCallRequest::from);

        ContractCallResponse msgSenderResponse = mirrorClient.contractsCall(contractCallRequest);
        int estimatedGas = Bytes.fromHexString(msgSenderResponse.getResult())
                .toBigInteger()
                .intValue();

        assertWithinDeviation(actualGasUsed.getActualGas(), estimatedGas, lowerDeviation, upperDeviation);
    }

    protected void validateGasEstimation(String data, ContractMethodInterface actualGasUsed, String solidityAddress) {
        validateGasEstimation(data, actualGasUsed, solidityAddress, Optional.empty());
    }

    /**
     * Asserts that a specific contract call results in a "400 Bad Request" response.
     * <p>
     * This method constructs a contract call request using the given encoded function call and contract address, and
     * then sends the request. It expects the call to result in a "400 Bad Request" response, and will throw an
     * assertion error if the response is anything other than that.
     *
     * @param data            The encoded function data to be sent.
     * @param contractAddress The address of the contract.
     * @throws AssertionError If the response from the contract call does not contain "400 Bad Request from POST".
     */
    protected void assertContractCallReturnsBadRequest(String data, String contractAddress) {
        var contractCallRequest =
                ModelBuilder.contractCallRequest().data(data).estimate(true).to(contractAddress);

        assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequest))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    protected void assertEthCallReturnsBadRequest(String block, String data, String contractAddress) {
        var contractCallRequest =
                ModelBuilder.contractCallRequest().block(block).data(data).to(contractAddress);

        assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequest))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }
}
