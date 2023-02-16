package com.hedera.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;

import com.hedera.mirror.test.e2e.acceptance.props.MirrorContractResult;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;

import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResultResponse;

import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ERCContractFeature extends AbstractFeature {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public static final String NAME_SELECTOR = "01984892";
    public static final String SYMBOL_SELECTOR = "a86e3576";
    public static final String DECIMALS_SELECTOR = "d449a832";
    public static final String TOTAL_SUPPLY_SELECTOR = "e4dc2aa4";
    public static final String BALANCE_OF_SELECTOR = "f7888aec";
    public static final String ALLOWANCE_SELECTOR = "927da105";
    public static final String GET_APPROVED_SELECTOR = "098f2366";
    public static final String IS_APPROVED_FOR_ALL_SELECTOR = "f49f40db";
    public static final String GET_OWNER_OF_SELECTOR = "d5d03e21";
    public static final String TOKEN_URI_SELECTOR = "e9dc6375";
    private static final String WRONG_SELECTOR = "000000";

    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;

    @Value("classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json")
    private Path ercContract;

    private ContractId contractId;
    private FileId fileId;
    private CompiledSolidityArtifact compiledSolidityArtifact;

    @Given("I successfully create a contract from contract bytes with {int} balance")
    public void createNewContract(int initialBalance) throws IOException {
        compiledSolidityArtifact = MAPPER.readValue(
                ResourceUtils.getFile(ercContract.toUri()),
                CompiledSolidityArtifact.class);
        createContract(compiledSolidityArtifact.getBytecode(), initialBalance);
    }

    @Then("I call the contract via the mirror node REST API")
    public void restContractCall() {
        var from = contractClient.getClientAddress();
        var to = contractId.toSolidityAddress();

        // TODO: assert results
        var getNameResponse = mirrorClient.contractsCall(NAME_SELECTOR, to, from);

        var getSymbolResponse = mirrorClient.contractsCall(SYMBOL_SELECTOR, to, from);

        var getDecimalsResponse = mirrorClient.contractsCall(DECIMALS_SELECTOR, to, from);

        var getTotalSupplyResponse = mirrorClient.contractsCall(TOTAL_SUPPLY_SELECTOR, to, from);

        var getBalanceOfResponse = mirrorClient.contractsCall(BALANCE_OF_SELECTOR, to, from);

        var getAllowanceResponse = mirrorClient.contractsCall(ALLOWANCE_SELECTOR, to, from);

        var getApprovedResponse = mirrorClient.contractsCall(GET_APPROVED_SELECTOR, to, from);

        var getIsApprovedForAllResponse = mirrorClient.contractsCall(IS_APPROVED_FOR_ALL_SELECTOR, to, from);

        var getOwnerOfResponse = mirrorClient.contractsCall(GET_OWNER_OF_SELECTOR, to, from);

        var getTokenURIResponse = mirrorClient.contractsCall(TOKEN_URI_SELECTOR, to, from);


        assertThatThrownBy(() -> mirrorClient.contractsCall(WRONG_SELECTOR, to, from))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Given("I successfully delete the contract")
    public void deleteContract() {
        networkTransactionResponse = contractClient.deleteContract(
                contractId,
                contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId(),
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should return status {int} for the contract transaction")
    public void verifyMirrorAPIResponses(int status) {
        log.info("Verify contract transaction");
        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId()).isEqualTo(contractId.toString());
    }

    @Then("the mirror node REST API should verify the deployed contract entity")
    public void verifyDeployedContractMirror() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById();
        verifyContractExecutionResultsByTransactionId();
    }


    private MirrorContractResponse verifyContractFromMirror(boolean isDeleted) {
        MirrorContractResponse mirrorContract = mirrorClient.getContractInfo(contractId.toString());

        assertNotNull(mirrorContract);
        assertThat(mirrorContract.getAdminKey()).isNotNull();
        assertThat(mirrorContract.getAdminKey().getKey())
                .isEqualTo(contractClient.getSdkClient().getExpandedOperatorAccountId().getPublicKey().toStringRaw());
        assertThat(mirrorContract.getAutoRenewPeriod()).isNotNull();
        assertThat(mirrorContract.getBytecode()).isNotBlank();
        assertThat(mirrorContract.getContractId()).isEqualTo(contractId.toString());
        assertThat(mirrorContract.getCreatedTimestamp()).isNotBlank();
        assertThat(mirrorContract.isDeleted()).isEqualTo(isDeleted);
        assertThat(mirrorContract.getFileId()).isEqualTo(fileId.toString());
        assertThat(mirrorContract.getMemo()).isNotBlank();
        String address = mirrorContract.getEvmAddress();
        assertThat(address).isNotBlank().isNotEqualTo("0x").isNotEqualTo("0x0000000000000000000000000000000000000000");
        assertThat(mirrorContract.getTimestamp()).isNotNull();
        assertThat(mirrorContract.getTimestamp().getFrom()).isNotNull();

        if (contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties().isSidecars()) {
            assertThat(mirrorContract.getRuntimeBytecode()).isNotNull();
        }

        assertThat(mirrorContract.getBytecode()).isEqualTo(compiledSolidityArtifact.getBytecode());

        if (isDeleted) {
            assertThat(mirrorContract.getObtainerId())
                    .isEqualTo(contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId().toString());
        } else {
            assertThat(mirrorContract.getObtainerId()).isNull();
        }

        return mirrorContract;
    }

    private void verifyContractExecutionResultsById() {
        List<MirrorContractResult> contractResults = mirrorClient.getContractResultsById(contractId.toString())
                .getResults();

        assertThat(contractResults).isNotEmpty().allSatisfy(this::verifyContractExecutionResults);
    }

    private void verifyContractExecutionResultsByTransactionId() {
        MirrorContractResultResponse contractResult = mirrorClient
                .getContractResultByTransactionId(networkTransactionResponse.getTransactionIdStringNoCheckSum());

        verifyContractExecutionResults(contractResult);
        assertThat(contractResult.getBlockHash()).isNotBlank();
        assertThat(contractResult.getBlockNumber()).isPositive();
        assertThat(contractResult.getHash()).isNotBlank();
    }

    private void verifyContractExecutionResults(MirrorContractResult contractResult) {
        ContractExecutionStage contractExecutionStage = isEmptyHex(contractResult.getFunctionParameters()) ?
                ContractExecutionStage.CREATION : ContractExecutionStage.CALL;

        assertThat(contractResult.getCallResult()).isNotBlank();
        assertThat(contractResult.getContractId()).isEqualTo(contractId.toString());
        String[] createdIds = contractResult.getCreatedContractIds();
        assertThat(createdIds).isNotEmpty();
        assertThat(contractResult.getErrorMessage()).isBlank();
        assertThat(contractResult.getFailedInitcode()).isBlank();
        assertThat(contractResult.getFrom()).isEqualTo(FeatureInputHandler.evmAddress(
                contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId()));
        assertThat(contractResult.getGasLimit())
                .isEqualTo(contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas());
        assertThat(contractResult.getGasUsed()).isPositive();
        assertThat(contractResult.getTo()).isEqualTo(FeatureInputHandler.evmAddress(contractId));

        int amount = 0; // no payment in contract construction phase
        int numCreatedIds = 2; // parent and child contract
        switch (contractExecutionStage) {
            case CREATION:
                amount = 10000000;
                assertThat(createdIds).contains(contractId.toString());
                assertThat(isEmptyHex(contractResult.getFunctionParameters())).isTrue();
                break;
            case CALL:
                numCreatedIds = 1;
                assertThat(createdIds).doesNotContain(contractId.toString());
                assertThat(isEmptyHex(contractResult.getFunctionParameters())).isFalse();
                break;
            default:
                break;
        }

        assertThat(contractResult.getAmount()).isEqualTo(amount);
        assertThat(createdIds).hasSize(numCreatedIds);
    }

    private void createContract(String byteCode, int initialBalance) {
        persistContractBytes(byteCode.replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
                null);

        verifyCreateContractNetworkResponse();
    }

    private void persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
        networkTransactionResponse = fileClient.createFile(new byte[] {});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);
        log.info("Created file {} to hold contract init code", fileId);

        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
    }

    private enum ContractExecutionStage {
        CREATION,
        CALL
    }

    private boolean isEmptyHex(String hexString) {
        return StringUtils.isEmpty(hexString) || hexString.equals("0x");
    }

}

