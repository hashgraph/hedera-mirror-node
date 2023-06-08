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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.junit.jupiter.api.Assertions.*;


@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ModificationEthCallFeature extends AbstractFeature {
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final String UPDATE_COUNTER_SELECTOR = "c648049d";
    private static final String STATE_UPDATE_N_TIMES_SELECTOR = "5256b99d";
    private static final String DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR = "cdb9c283";
    private static final String DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR = "ef043d57";
    private static final String DEPLOY_DESTROY_AND_REDEPLOY_SELECTOR = "1abd7d87";
    private static final String TRANSFER_SELECTOR = "39a92ada";
    private static final byte CREATE2_PREFIX = (byte) 0xFF;
    private static DeployedContract deployedContract;
    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;
    private final AccountClient accountClient;
    private String contractSolidityAddress;
    @Value("classpath:solidity/artifacts/contracts/EstimateGasContract.sol/EstimateGasContract.json")
    private Resource estimateGasTestContract;
    private CompiledSolidityArtifact compiledSolidityArtifacts;
    private String newAccountEvmAddress;
    private ExpandedAccountId receiverAccountId;


    public static String calculateCreate2Address(String senderAddress, String salt, String bytecode) throws DecoderException {
        byte[] senderAddressBytes = Hex.decodeHex(senderAddress);
        byte[] saltBytes = Hex.decodeHex(salt);
        byte[] bytecodeBytes = Hex.decodeHex(bytecode);

        byte[] data = new byte[1 + senderAddressBytes.length + saltBytes.length + bytecodeBytes.length];
        data[0] = CREATE2_PREFIX;
        System.arraycopy(senderAddressBytes, 0, data, 1, senderAddressBytes.length);
        System.arraycopy(saltBytes, 0, data, 1 + senderAddressBytes.length, saltBytes.length);
        System.arraycopy(bytecodeBytes, 0, data, 1 + senderAddressBytes.length + saltBytes.length, bytecodeBytes.length);

        byte[] result = DigestUtils.sha3_256(data);

        String resultHex = Hex.encodeHexString(result);
        return resultHex.substring(resultHex.length() - 40);  // Last 20 bytes (40 hex characters)
    }

    public static String[] splitAddresses(String result) {
        // remove the '0x' prefix
        String strippedResult = result.substring(2);

        // split into two addresses
        String address1 = strippedResult.substring(0, 64);
        String address2 = strippedResult.substring(64);

        // remove leading zeros and add '0x' prefix back
        address1 = new BigInteger(address1, 16).toString(16);
        address2 = new BigInteger(address2, 16).toString(16);

        return new String[]{address1, address2};
    }

    @Given("I successfully create contract from contract bytes with initial {int} balance")
    public void createNewEstimateContract(int supply) throws IOException {
        try (var in = estimateGasTestContract.getInputStream()) {
            compiledSolidityArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
            createContract(compiledSolidityArtifacts, supply);
        }
        deployedContract = createContract(compiledSolidityArtifacts, supply);
        contractSolidityAddress = deployedContract.contractId().toSolidityAddress();
        receiverAccountId = accountClient.createNewAccount(100);

    }

    @Then("I call eth call with update function and I expect return of the updated value")
    public void ethCallUpdateFunction() {
        var updateValue = "5";
        var updateCall = ContractCallRequest.builder()
                .data(UPDATE_COUNTER_SELECTOR + to32BytesString(updateValue))
                .from(contractClient.getClientAddress())
                .to(contractSolidityAddress)
                .estimate(false)
                .build();
        ContractCallResponse updateCallResponse =
                mirrorClient.contractsCall(updateCall);
        assertEquals(String.valueOf(updateCallResponse.getResultAsNumber()), updateValue);
    }

    @Then("I call eth call with update function that makes N times state update")
    public void ethCallStateUpdateNTimesFunction() {
        String updateValue = to32BytesString("10");
        var updateStateCall = ContractCallRequest.builder()
                .data(STATE_UPDATE_N_TIMES_SELECTOR + updateValue)
                .from(contractClient.getClientAddress())
                .to(contractSolidityAddress)
                .estimate(false)
                .build();

        ContractCallResponse updateStateCallResponse = mirrorClient.contractsCall(updateStateCall);

        assertEquals(String.valueOf(updateStateCallResponse.getResultAsNumber()), "15");
    }

    @Then("I call eth call with nested deploy using create function")
    public void ethCallNestedDeployViaCreateFunction() throws DecoderException {
        var deployCall = ContractCallRequest.builder()
                .data(DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(contractSolidityAddress)
                .estimate(false)
                .build();
        ContractCallResponse deployCallResponse =
                mirrorClient.contractsCall(deployCall);
        String[] addresses = splitAddresses(deployCallResponse.getResult());

        // Ensure that the addresses are not equal
        assertNotEquals(addresses[0], addresses[1]);

        // Ensure the strings are hexadecimal
        assertTrue(addresses[0].matches("^[0-9a-fA-F]+$"));
        assertTrue(addresses[1].matches("^[0-9a-fA-F]+$"));
    }

    @Then("I call eth call with nested deploy using create2 function")
    public void ethCallNestedDeployViaCreate2Function() {
        var deployCall = ContractCallRequest.builder()
                .data(DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(contractSolidityAddress)
                .estimate(false)
                .build();
        ContractCallResponse deployCallResponse =
                mirrorClient.contractsCall(deployCall);
    }

    @Then("I call eth call with function that executes nested deploy, destroy and redeploy")
    public void ethCallDeployDestroyAndRedeployFunction() {
        var reDeployCall = ContractCallRequest.builder()
                .data(DEPLOY_NESTED_CONTRACT_CONTRACT_VIA_CREATE2_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(contractSolidityAddress)
                .estimate(false)
                .build();
        ContractCallResponse redeployCallResponse =
                mirrorClient.contractsCall(reDeployCall);
    }

    @Then("I call eth call with transfer function that returns the balance")
    public void ethCallReentrancyCallFunction() {
        // representing the decimal number of 10000
        var transferValue = "2710";
        var transferCall = ContractCallRequest.builder()
                .data(TRANSFER_SELECTOR
                        + to32BytesString(receiverAccountId.getAccountId().toSolidityAddress())
                        + to32BytesString(transferValue))
                .from(contractClient.getClientAddress())
                .to(contractSolidityAddress)
                .estimate(false)
                .build();
        ContractCallResponse transferCallResponse =
                mirrorClient.contractsCall(transferCall);
        String[] balances = splitAddresses(transferCallResponse.getResult());

        //verify initial balance
        assertEquals(Integer.parseInt(balances[0], 16), 1000000);
        //verify balance after transfer of 10,000
        assertEquals(Integer.parseInt(balances[1], 16), 990000);
    }

    private DeployedContract createContract(CompiledSolidityArtifact compiledSolidityArtifact, int initialBalance) {
        var fileId = persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(fileId,
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
                null);
        var contractId = verifyCreateContractNetworkResponse();

        return new DeployedContract(fileId, contractId, compiledSolidityArtifact);
    }

    private FileId persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
        networkTransactionResponse = fileClient.createFile(new byte[]{});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);

        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        return fileId;
    }

    private ContractId verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
        return contractId;
    }

    private record DeployedContract(FileId fileId, ContractId contractId,
                                    CompiledSolidityArtifact compiledSolidityArtifact) {
    }
}