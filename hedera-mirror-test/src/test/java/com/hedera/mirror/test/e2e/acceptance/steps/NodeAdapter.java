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

package com.hedera.mirror.test.e2e.acceptance.steps;

import static com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties.EquivalenceNodeConfiguration.CONSENSUS;
import static com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties.EquivalenceNodeConfiguration.MIRROR;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_CALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_DESTRUCT;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.EquivalenceContractMethods;
import jakarta.inject.Named;
import java.math.BigInteger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A utility class that provides methods to call contract functions against both consensus and mirror nodes. There are utility methods
 * that extract results of the same type from the different response types from the different nodes. The end result from both nodes is validated against the same rules and expected result.
 * Thus, we can check if both nodes have equivalent behaviour.
 * */
@Named
public class NodeAdapter extends AbstractFeature {

    private static final String OBTAINER_SAME_CONTRACT_ID_EXCEPTION = "OBTAINER_SAME_CONTRACT_ID";
    private static final String INVALID_SOLIDITY_ADDRESS_EXCEPTION = "INVALID_SOLIDITY_ADDRESS";
    private static final String TRANSACTION_SUCCESSFUL_MESSAGE = "Transaction successful";

    @Autowired
    private AcceptanceTestProperties acceptanceTestProperties;

    private void assertStatus(final String message) {
        final var status = extractStatus(message);
        assertEquals(OBTAINER_SAME_CONTRACT_ID_EXCEPTION, status);
    }

    private long extractAccountNumber(String account) {
        String[] parts = account.split("\\.");
        return Long.parseLong(parts[parts.length - 1]);
    }

    private String executeContractCallTransaction(
            DeployedContract deployedContract, String functionName, ContractFunctionParameters parameters) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    deployedContract.contractId(),
                    contractClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    functionName,
                    parameters,
                    null);

            networkTransactionResponse = executeContractResult.networkTransactionResponse();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
            return "Transaction successful";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private ContractFunctionResult executeContractCallQuery(
            DeployedContract deployedContract, String functionName, ContractFunctionParameters parameters) {
        return contractClient.executeContractQuery(
                deployedContract.contractId(),
                functionName,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                parameters);
    }

    public static String extractStatus(String transactionResult) {
        String key = "status=";
        int statusIndex = transactionResult.indexOf(key);

        if (statusIndex != -1) {
            int startIndex = statusIndex + key.length();
            int endIndex = transactionResult.indexOf(',', startIndex);
            endIndex = endIndex != -1 ? endIndex : transactionResult.length();
            return transactionResult.substring(startIndex, endIndex);
        }

        return "Status not found";
    }

    public void testCallsWithNumericResult(
            final DeployedContract contractToCall,
            final EquivalenceContractMethods method,
            final BigInteger expectedResult,
            final String address,
            boolean isForSystemAccount) {
        if (CONSENSUS.equals(acceptanceTestProperties.getEquivalenceNodeConfiguration())) {
            validateConsensusContractCallQueryNumeric(
                    contractToCall, method, expectedResult, address, isForSystemAccount);
        } else if (MIRROR.equals(acceptanceTestProperties.getEquivalenceNodeConfiguration())) {
            validateMirrorContractCallNumeric(
                    contractToCall.contractId().toSolidityAddress(), method, expectedResult, address);
        } else {
            validateConsensusContractCallQueryNumeric(
                    contractToCall, method, expectedResult, address, isForSystemAccount);
            validateMirrorContractCallNumeric(
                    contractToCall.contractId().toSolidityAddress(), method, expectedResult, address);
        }
    }

    public void testCallsWithBytesResult(
            final DeployedContract contract, final EquivalenceContractMethods method, final String address) {
        if (CONSENSUS.equals(acceptanceTestProperties.getEquivalenceNodeConfiguration())) {
            validateConsensusContractCallQueryBytes(contract, method, address);
        } else if (MIRROR.equals(acceptanceTestProperties.getEquivalenceNodeConfiguration())) {
            validateMirrorContractCallBytes(contract.contractId().toSolidityAddress(), method, address);
        } else {
            validateConsensusContractCallQueryBytes(contract, method, address);
            validateMirrorContractCallBytes(contract.contractId().toSolidityAddress(), method, address);
        }
    }

    public void testCallsWithStatusResult(
            final DeployedContract deployedContract,
            final EquivalenceContractMethods method,
            final String addressParameter,
            final boolean isForSystemContract) {
        if (CONSENSUS.equals(acceptanceTestProperties.getEquivalenceNodeConfiguration())) {
            validateConsensusContractCallQueryStatus(deployedContract, method, addressParameter, isForSystemContract);
        } else if (MIRROR.equals(acceptanceTestProperties.getEquivalenceNodeConfiguration())) {
            validateMirrorContractCallStatus(
                    deployedContract.contractId().toSolidityAddress(), method, addressParameter, isForSystemContract);
        } else {
            validateConsensusContractCallQueryStatus(deployedContract, method, addressParameter, isForSystemContract);
            validateMirrorContractCallStatus(
                    deployedContract.contractId().toSolidityAddress(), method, addressParameter, isForSystemContract);
        }

        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
    }

    private byte[] getBytesResultFromContractCallQuery(
            final DeployedContract contract,
            final String functionName,
            final ContractFunctionParameters contractFunctionParameters) {
        var functionResult = executeContractCallQuery(contract, functionName, contractFunctionParameters);
        return functionResult.getBytes(0);
    }

    private BigInteger getBigIntegerResultFromContractCallQuery(
            final DeployedContract contract,
            final String functionName,
            final ContractFunctionParameters contractFunctionParameters) {
        var functionResult = executeContractCallQuery(contract, functionName, contractFunctionParameters);
        return functionResult.getInt256(0);
    }

    private ContractFunctionParameters getParametersForContractCallQuery(final String address) {
        final var accountAddress = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        return new ContractFunctionParameters().addAddress(accountAddress);
    }

    private BigInteger getBigIntegerContractCallResult(
            final String contractToCall, final EquivalenceContractMethods method, final String addressParameter) {
        var data = encodeData(EQUIVALENCE_CALL, method, asAddress(addressParameter));
        var response = callContract(data, contractToCall);
        return response.getResultAsNumber();
    }

    private byte[] getBytesContractCallResult(
            final String contractToCall, final EquivalenceContractMethods method, final String addressParameter) {
        var data = encodeData(EQUIVALENCE_CALL, method, asAddress(addressParameter));
        var response = callContract(data, contractToCall);
        return response.getResultAsBytes().toArray();
    }

    private String getErrorMessageFromContractCallResult(
            final String contractToCall, final EquivalenceContractMethods method, final String addressParameter) {
        var data = encodeData(EQUIVALENCE_CALL, method, asAddress(addressParameter));
        var response = callContract(data, contractToCall);
        return response.getResultAsText();
    }

    private void validateConsensusContractCallQueryBytes(
            final DeployedContract contract, final EquivalenceContractMethods method, final String addressParameter) {
        final var result = getBytesResultFromContractCallQuery(
                contract, method.name(), getParametersForContractCallQuery(addressParameter));
        assertBytes(result);
    }

    private void validateMirrorContractCallBytes(
            final String contractToCall, final EquivalenceContractMethods method, final String addressParameter) {
        final var result = getBytesContractCallResult(contractToCall, method, addressParameter);
        assertBytes(result);
    }

    public void validateConsensusContractCallQueryNumeric(
            final DeployedContract contract,
            final EquivalenceContractMethods method,
            final BigInteger expectedResult,
            final String addressParameter,
            final boolean isForSystemAccount) {
        final var result = getBigIntegerResultFromContractCallQuery(
                contract, method.name(), getParametersForContractCallQuery(addressParameter));

        if (isForSystemAccount) {
            assertBalanceForSystemAccount(addressParameter, result);
        } else {
            assertNumeric(expectedResult, result);
        }
    }

    private void validateMirrorContractCallNumeric(
            final String contractToCall,
            final EquivalenceContractMethods method,
            final BigInteger expectedResult,
            final String addressParameter) {
        final var result = getBigIntegerContractCallResult(contractToCall, method, addressParameter);
        assertNumeric(expectedResult, result);
    }

    public void validateConsensusContractCallQueryStatus(
            final DeployedContract deployedContract,
            final EquivalenceContractMethods method,
            final String addressParameter,
            boolean isForSystemAccount) {
        final var message = executeContractCallTransaction(
                deployedContract, method.name(), getParametersForContractCallQuery(addressParameter));

        if (isForSystemAccount) {
            final var status = extractStatus(message);
            assertSelfDestructStatusForSystemAccount(addressParameter, message, status);
        } else {
            assertStatus(message);
        }
    }

    public void validateMirrorContractCallStatus(
            final String contractToCall,
            final EquivalenceContractMethods method,
            final String addressParameter,
            boolean isForSystemAccount) {
        final var message = getErrorMessageFromContractCallResult(contractToCall, method, addressParameter);

        if (isForSystemAccount) {
            final var status = extractStatus(message);
            assertSelfDestructStatusForSystemAccount(addressParameter, message, status);
        } else {
            assertStatus(message);
        }
    }

    private void assertBalanceForSystemAccount(final String accountAddress, final BigInteger result) {
        if (extractAccountNumber(accountAddress) < 751) {
            assertNumeric(BigInteger.ZERO, result);
        } else {
            assertTrue(result.longValue() > 1);
        }
    }

    private void assertSelfDestructStatusForSystemAccount(
            final String beneficiary, final String message, final String status) {
        if (extractAccountNumber(beneficiary) < 751) {
            assertEquals(INVALID_SOLIDITY_ADDRESS_EXCEPTION, status);
        } else {
            assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
        }
    }

    private void assertBytes(final byte[] result) {
        assertArrayEquals(new byte[0], result);
    }

    private void assertNumeric(final BigInteger expected, final BigInteger result) {
        assertEquals(expected, result);
    }
}
