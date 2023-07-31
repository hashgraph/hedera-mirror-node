package com.hedera.mirror.test.e2e.acceptance.steps;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.Strings;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorNftResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import com.esaulpaugh.headlong.abi.Function;

import java.nio.ByteBuffer;
import java.math.BigInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asLongArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asHeadlongAddressArray;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asHeadlongAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.TokenTransferListBuilder;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.nftAmount;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesStringRightPad;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.accountAmount;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asHeadlongByteArray;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.LinkedHashMap;

import org.json.JSONObject;

import java.util.Map;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EstimatePrecompileFeature extends AbstractFeature {
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final String HEX_DIGITS = "0123456789abcdef";
    private static final Tuple[] EMPTY_TUPLE_ARRAY = new Tuple[]{};

    private static final String RANDOM_ADDRESS = to32BytesString(RandomStringUtils.random(40, HEX_DIGITS));
    private static final long firstNftSerialNumber = 1;
    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;
    private final List<TokenId> tokenIds = new ArrayList<>();
    private final AccountClient accountClient;
    private DeployedContract deployedEstimatePrecompileContract;
    private DeployedContract deployedErcTesteContract;
    private ExpandedAccountId receiverAccount;
    private ExpandedAccountId secondReceiverAccount;
    private ExpandedAccountId admin;
    private String estimatePrecompileContractSolidityAddress;
    private String ercTestContractSolidityAddress;
    private int lowerDeviation;
    private int upperDeviation;
    @Value("classpath:solidity/artifacts/contracts/EstimatePrecompileContract.sol/EstimatePrecompileContract.json")
    private Resource estimatePrecompileTestContract;

    @Value("classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json")
    private Resource ercTestContract;
    private CompiledSolidityArtifact compiledEstimatePrecompileSolidityArtifacts;
    private CompiledSolidityArtifact compiledErcTestContractSolidityArtifacts;
    private String newAccountEvmAddress;

    /**
     * Checks if the actualUsedGas is within the specified range of the estimatedGas.
     * <p>
     * The method calculates the lower and upper bounds as percentages of the actualUsedGas, then checks if the
     * estimatedGas is within the range (inclusive) and returns true if it is, otherwise returns false.
     *
     * @param actualUsedGas     the integer value that represents the expected value
     * @param estimatedGas      the integer value to be checked
     * @param lowerBoundPercent the integer percentage value for the lower bound of the acceptable range
     * @param upperBoundPercent the integer percentage value for the upper bound of the acceptable range
     * @return true if the actualUsedGas is within the specified range, false otherwise
     */
    public static boolean isWithinDeviation(int actualUsedGas, int estimatedGas, int lowerBoundPercent,
                                            int upperBoundPercent) {
        int lowerDeviation = actualUsedGas * lowerBoundPercent / 100;
        int upperDeviation = actualUsedGas * upperBoundPercent / 100;

        int lowerBound = actualUsedGas + lowerDeviation;
        int upperBound = actualUsedGas + upperDeviation;

        return (estimatedGas >= lowerBound) && (estimatedGas <= upperBound);
    }


    @Given("I create estimate precompile contract with {int} balance")
    public void createNewEstimateContract(int supply) throws IOException {
        try (var in = estimatePrecompileTestContract.getInputStream()) {
            compiledEstimatePrecompileSolidityArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
            createContract(compiledEstimatePrecompileSolidityArtifacts, supply);
        }
        deployedEstimatePrecompileContract = createContract(compiledEstimatePrecompileSolidityArtifacts, supply);
        estimatePrecompileContractSolidityAddress = deployedEstimatePrecompileContract.contractId().toSolidityAddress();
        newAccountEvmAddress = PrivateKey.generateECDSA().getPublicKey().toEvmAddress().toString();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        receiverAccount = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
        secondReceiverAccount = accountClient.getAccount(AccountClient.AccountNameEnum.ALICE);
    }

    @Given("I create erc test contract with {int} balance")
    public void createNewERCContract(int supply) throws IOException {
        try (var in = ercTestContract.getInputStream()) {
            compiledErcTestContractSolidityArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
            createContract(compiledErcTestContractSolidityArtifacts, supply);
        }
        deployedErcTesteContract = createContract(compiledErcTestContractSolidityArtifacts, supply);
        ercTestContractSolidityAddress = deployedErcTesteContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create and verify a fungible token for estimateGas precompile tests")
    public void createFungibleToken() {
        createTokenWithCustomFees(1);
    }

    @Given("I successfully create and verify a non fungible token for estimateGas precompile tests")
    public void createNonFungibleToken() {
        createNonFungibleTokenWithFixedFee(1);
    }

    @Given("I mint and verify a new nft")
    public void mintNft() {
        NetworkTransactionResponse tx = tokenClient.mint(tokenIds.get(1), RandomUtils.nextBytes(4));
        assertNotNull(tx.getTransactionId());
        TransactionReceipt receipt = tx.getReceipt();
        assertNotNull(receipt);
        assertThat(receipt.serials.size()).isOne();

        verifyNft(tokenIds.get(1), firstNftSerialNumber);
    }

    @And("I set lower deviation at {int}% and upper deviation at {int}%")
    public void setDeviations(int lower, int upper) {
        lowerDeviation = lower;
        upperDeviation = upper;
    }

    public String getAbiFunctionAsJsonString(CompiledSolidityArtifact compiledSolidityArtifact, String functionName) {
        Optional<Object> function = Arrays.stream(compiledSolidityArtifact.getAbi())
                .filter(item -> ((LinkedHashMap) item).get("name").equals(functionName))
                .findFirst();

        return (new JSONObject((Map) function.get())).toString();
    }

    public Function getFunctionFromArtifact(String functionName) {
        String json = getAbiFunctionAsJsonString(compiledEstimatePrecompileSolidityArtifacts, functionName);
        return Function.fromJson(json);
    }

    @Then("I call estimateGas with associate function for fungible token")
    public void associateFunctionEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.ASSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with associate function for NFT")
    public void associateFunctionNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.ASSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with dissociate token function without association for fungible token")
    public void dissociateFunctionEstimateGasNegative() {
        //attempt to call dissociate function without having association
        //expecting status 400/revert
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DISSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with dissociate token function without association for NFT")
    public void dissociateFunctionNFTEstimateGasNegative() {
        //attempt to call dissociate function without having association
        //expecting status 400/revert
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DISSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for fungible token")
    public void nestedAssociateFunctionEstimateGas() {
        //attempt to call associate function twice
        //expecting a revert
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.NESTED_ASSOCIATE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for NFT")
    public void nestedAssociateFunctionNFTEstimateGas() {
        //attempt to call associate function twice
        //expecting a revert
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.NESTED_ASSOCIATE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with dissociate token function for fungible token")
    public void dissociateFunctionEstimateGas() {
        //associating the token with the address
        networkTransactionResponse = tokenClient.associate(receiverAccount, tokenIds.get(0));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DISSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with dissociate token function for NFT")
    public void dissociateFunctionNFTEstimateGas() {
        //associating the NFT with the address
        networkTransactionResponse = tokenClient.associate(receiverAccount, tokenIds.get(1));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DISSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with dissociate and associate nested function for fungible token")
    public void dissociateAndAssociatedEstimateGas() {
        //token is already associated
        //attempting to execute nested dissociate and associate function
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DISSOCIATE_AND_ASSOCIATE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_AND_ASSOCIATE.getActualGas());
    }

    @Then("I call estimateGas with dissociate and associate nested function for NFT")
    public void dissociateAndAssociatedNFTEstimateGas() {
        //token is already associated
        //attempting to execute nested dissociate and associate function
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DISSOCIATE_AND_ASSOCIATE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_AND_ASSOCIATE.getActualGas());
    }

    @Then("I call estimateGas with approve function without association")
    public void approveWithoutAssociationEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.APPROVE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("10"));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with setApprovalForAll function without association")
    public void setApprovalForAllWithoutAssociationEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.SET_APPROVAL_FOR_ALL.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        true);

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with approveNFT function without association")
    public void approveNonFungibleWithoutAssociationEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.APPROVE_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("1"));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I associate contracts with the tokens and approve the all nft serials")
    public void associateTokensWithContract() throws InvalidProtocolBufferException {
        //In order to execute Approve, approveNFT, ercApprove we need to associate the contract with the token
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), tokenIds.get(0));
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), tokenIds.get(1));
        tokenClient.associate(deployedErcTesteContract.contractId(), tokenIds.get(0));
        //approve is also needed for the approveNFT function
        networkTransactionResponse = accountClient.approveNftAllSerials(tokenIds.get(1), deployedEstimatePrecompileContract.contractId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I call estimateGas with approve function")
    public void approveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.APPROVE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("10"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.APPROVE.getActualGas());
    }

    @Then("I call estimateGas with approveNFT function")
    public void approveNftEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.APPROVE_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("1"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.APPROVE_NFT.getActualGas());
    }

    @Then("I call estimateGas with ERC approve function")
    public void ercApproveEstimateGas() {
        Function function = Function.fromJson(getAbiFunctionAsJsonString(compiledErcTestContractSolidityArtifacts, ContractMethods.APPROVE_ERC.getFunctionName()));
        ByteBuffer encodedFunctionCall = function
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("10"));

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(ercTestContractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse msgSenderResponse =
                mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();

        assertTrue(isWithinDeviation(ContractMethods.APPROVE_ERC.getActualGas(), estimatedGas, lowerDeviation, upperDeviation));
    }

    @Then("I call estimateGas with setApprovalForAll function")
    public void setApprovalForAllEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.SET_APPROVAL_FOR_ALL.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        true);

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.SET_APPROVAL_FOR_ALL.getActualGas());
    }

    @Then("I call estimateGas with transferFrom function without approval")
    public void transferFromEstimateGasWithoutApproval() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.TRANSFER_FROM.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("5"));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with ERC transferFrom function without approval")
    public void ercTransferFromEstimateGasWithoutApproval() {
        Function function = Function.fromJson(getAbiFunctionAsJsonString(compiledErcTestContractSolidityArtifacts,
                ContractMethods.TRANSFER_FROM_ERC.getFunctionName()));
        ByteBuffer encodedFunctionCall = function
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("10"));

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(ercTestContractSolidityAddress)
                .estimate(true)
                .build();

        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with transferFrom function")
    public void transferFromEstimateGas() {
        networkTransactionResponse = tokenClient.grantKyc(tokenIds.get(0),receiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        networkTransactionResponse = accountClient.approveToken(tokenIds.get(0), receiverAccount.getAccountId(), 10);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.TRANSFER_FROM.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("5"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_FROM.getActualGas());
    }

    @Then("I call estimateGas with ERC transferFrom function")
    public void ercTransferFromEstimateGas() {
        Function function = Function.fromJson(getAbiFunctionAsJsonString(compiledErcTestContractSolidityArtifacts,
                ContractMethods.TRANSFER_FROM_ERC.getFunctionName()));
        ByteBuffer encodedFunctionCall = function
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("5"));

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(ercTestContractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse msgSenderResponse =
                mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();

        assertTrue(isWithinDeviation(ContractMethods.TRANSFER_FROM_ERC.getActualGas(), estimatedGas, lowerDeviation, upperDeviation));
    }

    @Then("I call estimateGas with transferFrom function with more than the approved allowance")
    public void transferFromExceedAllowanceEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.TRANSFER_FROM.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("500"));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with ERC transferFrom function with more than the approved allowance")
    public void ercTransferFromExceedsAllowanceEstimateGas() {
        Function function = Function.fromJson(getAbiFunctionAsJsonString(compiledErcTestContractSolidityArtifacts,
                ContractMethods.TRANSFER_FROM_ERC.getFunctionName()));
        ByteBuffer encodedFunctionCall = function
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("500"));

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(ercTestContractSolidityAddress)
                .estimate(true)
                .build();

        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with transferFromNFT function")
    public void transferFromNFTEstimateGas() {
        NftId id = new NftId(tokenIds.get(1), firstNftSerialNumber);

        networkTransactionResponse = accountClient.approveNft(id, receiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        networkTransactionResponse = tokenClient.grantKyc(tokenIds.get(1), receiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        //tokenClient.transferNonFungibleToken(tokenIds.get(1), admin, receiverAccount.getAccountId(), Collections.singletonList(firstNftSerialNumber));

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.TRANSFER_FROM_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("1"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_FROM_NFT.getActualGas());
    }

    @Then("I call estimateGas with transferFromNFT with invalid serial number")
    public void transferFromNFTInvalidSerialEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.TRANSFER_FROM_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("50"));
        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with transferToken function")
    public void transferTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.TRANSFER_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        5L);
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with transferNFT function")
    public void transferNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.TRANSFER_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        1L);
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_NFT.getActualGas());
    }

    @Then("I call estimateGas with ERC transfer function")
    public void ercTransferEstimateGas() {
        tokenClient.associate(receiverAccount, tokenIds.get(6));
        networkTransactionResponse = accountClient.approveToken(tokenIds.get(6), receiverAccount.getAccountId(), 50L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        Function function = Function.fromJson(getAbiFunctionAsJsonString(compiledErcTestContractSolidityArtifacts,
                ContractMethods.TRANSFER_ERC.getFunctionName()));
        ByteBuffer encodedFunctionCall = function
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(6).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("5"));

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(ercTestContractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse msgSenderResponse =
                mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();

        assertTrue(isWithinDeviation(ContractMethods.TRANSFER_ERC.getActualGas(), estimatedGas, lowerDeviation, upperDeviation));
    }

    @Then("I create 2 more fungible tokens")
    public void createAnotherFungibleToken() {
        for (int i = 0; i < 2; i++) {
            createTokenWithCustomFees(1);
        }
    }

    @Then("I create 2 more NFTs")
    public void createAnotherNFT() {
        for (int i = 0; i < 2; i++) {
            createNonFungibleTokenWithFixedFee(1);
        }
        NetworkTransactionResponse tx = tokenClient.mint(tokenIds.get(4), RandomUtils.nextBytes(4));
        assertNotNull(tx.getTransactionId());
        TransactionReceipt receipt = tx.getReceipt();
        assertNotNull(receipt);
        assertThat(receipt.serials.size()).isOne();

        NetworkTransactionResponse secondTx = tokenClient.mint(tokenIds.get(5), RandomUtils.nextBytes(4));
        assertNotNull(secondTx.getTransactionId());
        TransactionReceipt secondReceipt = secondTx.getReceipt();
        assertNotNull(secondReceipt);
        assertThat(secondReceipt.serials.size()).isOne();
    }

    @Then("I call estimateGas with associateTokens function for fungible tokens")
    public void associateTokensEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.ASSOCIATE_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(tokenIds.get(2).toSolidityAddress(), tokenIds.get(3).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with associateTokens function for NFTs")
    public void associateNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.ASSOCIATE_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(tokenIds.get(4).toSolidityAddress(), tokenIds.get(5).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with dissociateTokens function for fungible tokens")
    public void dissociateTokensEstimateGas() {
        //associating tokens with the address
        tokenClient.associate(receiverAccount, tokenIds.get(2));
        networkTransactionResponse = tokenClient.associate(receiverAccount, tokenIds.get(3));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DISSOCIATE_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(tokenIds.get(2).toSolidityAddress(), tokenIds.get(3).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with dissociateTokens function for NFTs")
    public void dissociateNFTEstimateGas() {
        //associating tokens with the address
        tokenClient.associate(receiverAccount, tokenIds.get(4));
        networkTransactionResponse = tokenClient.associate(receiverAccount, tokenIds.get(5));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DISSOCIATE_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(tokenIds.get(4).toSolidityAddress(), tokenIds.get(5).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with transferTokens function")
    public void transferTokensEstimateGas() {
        tokenClient.associate(secondReceiverAccount,tokenIds.get(0));
        networkTransactionResponse = tokenClient.grantKyc(tokenIds.get(0),secondReceiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        networkTransactionResponse = accountClient.approveToken(tokenIds.get(0), secondReceiverAccount.getAccountId(), 10);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.TRANSFER_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(admin.getAccountId().toSolidityAddress(),
                                receiverAccount.getAccountId().toSolidityAddress(),
                                secondReceiverAccount.getAccountId().toSolidityAddress())),
                                new long[]{-6L, 3L, 3L});

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with transferNFTs function")
    public void transferNFTsEstimateGas() {
        tokenClient.mint(tokenIds.get(5), RandomUtils.nextBytes(4));
        //tokenClient.associate(receiverAccount, tokenIds.get(5));
        tokenClient.associate(secondReceiverAccount, tokenIds.get(5));
        tokenClient.grantKyc(tokenIds.get(5), admin.getAccountId());
        tokenClient.grantKyc(tokenIds.get(5), receiverAccount.getAccountId());
        tokenClient.grantKyc(tokenIds.get(5), secondReceiverAccount.getAccountId());
        accountClient.approveNftAllSerials(tokenIds.get(5), receiverAccount.getAccountId());
        networkTransactionResponse = accountClient.approveNftAllSerials(tokenIds.get(5), secondReceiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.TRANSFER_NFTS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(5).toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(admin.getAccountId().toSolidityAddress())),
                        asHeadlongAddressArray(Arrays.asList(receiverAccount.getAccountId().toSolidityAddress(),
                                secondReceiverAccount.getAccountId().toSolidityAddress())),
                        new long[]{1, 2});

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_NFTS.getActualGas());
    }

    @Then("I call estimateGas with cryptoTransfer function for hbars")
    public void cryptoTransferHbarEstimateGas() {
        var senderTransfer = accountAmount(admin.getAccountId().toSolidityAddress(), -10L, false);
        var receiverTransfer = accountAmount(receiverAccount.getAccountId().toSolidityAddress(), 10L, false);
        var args = Tuple.of((Object) new Tuple []{senderTransfer, receiverTransfer});
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.CRYPTO_TRANSFER_HBARS.getFunctionName())
                .encodeCallWithArgs(args, EMPTY_TUPLE_ARRAY);
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CRYPTO_TRANSFER_HBARS.getActualGas());
    }

    private TokenTransferListBuilder tokenTransferList() {
        return new TokenTransferListBuilder();
    }

    @Then("I call estimateGas with cryptoTransfer function for nft")
    public void cryptoTransferNFTEstimateGas() {
        var tokenTransferList = (Object) new Tuple[]{tokenTransferList()
                .forToken(tokenIds.get(1).toSolidityAddress())
                .withNftTransfers(
                        nftAmount(admin.getAccountId().toSolidityAddress(),
                                receiverAccount.getAccountId().toSolidityAddress(),
                                1L,
                                false))
                .build()};
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.CRYPTO_TRANSFER_NFT.getFunctionName())
                .encodeCallWithArgs(Tuple.of((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CRYPTO_TRANSFER_NFT.getActualGas());
    }

    @Then("I call estimateGas with cryptoTransfer function for fungible tokens")
    public void cryptoTransferFungibleEstimateGas() {
        tokenClient.grantKyc(tokenIds.get(0),receiverAccount.getAccountId());
        networkTransactionResponse = tokenClient.transferFungibleToken(tokenIds.get(0),admin,receiverAccount.getAccountId(),5L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var tokenTransferList = (Object) new Tuple[]{tokenTransferList()
                .forToken(tokenIds.get(0).toSolidityAddress())
                .withAccountAmounts(
                        accountAmount(secondReceiverAccount.getAccountId().toSolidityAddress(), 5L, false),
                        accountAmount(admin.getAccountId().toSolidityAddress(), -5L, false))
                .build()};
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.CRYPTO_TRANSFER.getFunctionName())
                .encodeCallWithArgs(Tuple.of((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);


        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CRYPTO_TRANSFER.getActualGas());

//        //amount equals to -5 (Two's compliment)
//        String spendingValue = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffb";
//
//        StringBuilder requestData = new StringBuilder();
//        requestData.append(ContractMethods.CRYPTO_TRANSFER.getFunctionName())
//                .append(to32BytesString("40")) // Offset to start of 'cryptoTransfers' struct data
//                .append(to32BytesString("80")) // Offset to start of 'tokenTransferList' array data
//                .append(to32BytesString("20")) // Length of 'cryptoTransfers.transfers' array
//                .append(to32BytesString("0")) // Length of 'cryptoTransfers.nftTransfers' array
//                .append(to32BytesString("1")) // Length of 'tokenTransferList' array: 1
//                .append(to32BytesString("20")) // Offset to start of 'tokenTransferList[0]' struct data
//                .append(to32BytesString(tokenIds.get(0).toSolidityAddress())) // 'tokenTransferList[0].token' address value
//                .append(to32BytesString("60")) // Offset to start of 'tokenTransferList[0].transfers' array data
//                .append(to32BytesString("140")) // Offset to start of 'tokenTransferList[0].nftTransfers' array data (empty in this case)
//                .append(to32BytesString("2")) // Length of 'tokenTransferList[0].transfers' array: 2
//                .append(to32BytesString(receiverAccount.getAccountId().toSolidityAddress())) // 'tokenTransferList[0].transfers[0].accountID' address value
//                .append(to32BytesString("5")) // 'tokenTransferList[0].transfers[0].amount' value
//                .append(to32BytesString("0")) // Length of 'tokenTransferList[0].transfers[0].serialNumbers' array
//                .append(to32BytesString(admin.getAccountId().toSolidityAddress())) // 'tokenTransferList[0].transfers[1].accountID' address value
//                .append(spendingValue) // 'tokenTransferList[0].transfers[1].amount' value (-5)
//                .append(to32BytesString("0")) // Length of 'tokenTransferList[0].transfers[1].serialNumbers' array
//                .append(to32BytesString("0")); // Length of 'tokenTransferList[0].nftTransfers' array (empty in this case)
//
//        validateGasEstimation(requestData.toString(), ContractMethods.CRYPTO_TRANSFER.getActualGas());
    }

    @Then("I call estimateGas with mintToken function for fungible token")
    public void mintFungibleTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.MINT_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        1L,
                        asHeadlongByteArray(new ArrayList<>()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.MINT_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with mintToken function for NFT")
    public void mintNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.MINT_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        0L,
                        asHeadlongByteArray(Arrays.asList("0x02")));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.MINT_NFT.getActualGas());
    }

    @Then("I call estimateGas with burnToken function for fungible token")
    public void burnFungibleTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.BURN_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        1L,
                        asLongArray(new ArrayList<>()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.BURN_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with burnToken function for NFT")
    public void burnNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.BURN_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        0L,
                        asLongArray(List.of(1L)));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.BURN_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with CreateFungibleToken function")
    public void createFungibleTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.CREATE_FUNGIBLE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CREATE_FUNGIBLE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with CreateNFT function")
    public void createNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.CREATE_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CREATE_NFT.getActualGas());
    }

    @Then("I call estimateGas with CreateFungibleToken function with custom fees")
    public void createFungibleTokenWithCustomFeesEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getActualGas());
    }

    @Then("I call estimateGas with CreateNFT function with custom fees")
    public void createNFTWithCustomFeesEstimateGas() {
        validateGasEstimation(ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES.getFunctionName()
                + to32BytesString(admin.getAccountId().toSolidityAddress())
                + to32BytesString(tokenIds.get(0).toSolidityAddress()), ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES.getActualGas());


        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getActualGas());
    }

    @Then("I call estimateGas with WipeTokenAccount function")
    public void wipeTokenAccountEstimateGas() {
        tokenClient.grantKyc(tokenIds.get(0), receiverAccount.getAccountId());
        accountClient.approveToken(tokenIds.get(0), receiverAccount.getAccountId(), 10_000_000_000L);
        networkTransactionResponse = tokenClient.transferFungibleToken(tokenIds.get(0), admin, receiverAccount.getAccountId(), 3_000_000_000_0L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.WIPE_TOKEN_ACCOUNT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        1L);

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.WIPE_TOKEN_ACCOUNT.getActualGas());
    }

    @Then("I call estimateGas with WipeTokenAccount function with invalid amount")
    public void wipeTokenAccountInvalidAmountEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.WIPE_TOKEN_ACCOUNT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        100000000000000000L);

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with WipeNFTAccount function")
    public void wipeNFTAccountEstimateGas() {
        tokenClient.grantKyc(tokenIds.get(1), receiverAccount.getAccountId());
        NftId id = new NftId(tokenIds.get(1), firstNftSerialNumber);
        accountClient.approveNft(id, receiverAccount.getAccountId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(tokenIds.get(1), admin, receiverAccount.getAccountId(), Collections.singletonList(firstNftSerialNumber));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.WIPE_NFT_ACCOUNT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asLongArray(List.of(1L)));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.WIPE_NFT_ACCOUNT.getActualGas());
    }

    @Then("I call estimateGas with WipeNFTAccount function with invalid serial number")
    public void wipeNFTAccountInvalidSerialNumberEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.WIPE_NFT_ACCOUNT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asLongArray(List.of(66L)));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with GrantKYC function for fungible token")
    public void grantKYCFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.GRANT_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GRANT_KYC.getActualGas());
    }

    @Then("I call estimateGas with GrantKYC function for NFT")
    public void grantKYCNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.GRANT_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GRANT_KYC.getActualGas());
    }

    @Then("I create fungible and non-fungible token without KYC status")
    public void createFungibleTokenWithoutKYC() {
        createTokenWithCustomFees(TokenKycStatus.KycNotApplicable_VALUE);
        createNonFungibleTokenWithFixedFee(TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Then("I call estimateGas with GrantKYC function for fungible token without KYC status")
    public void grantKYCFungibleNegativeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.GRANT_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(6).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with GrantKYC function for NFT without KYC status")
    public void grantKYCNonFungibleNegativeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.GRANT_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(7).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }


    @Then("I call estimateGas with RevokeTokenKYC function for fungible token")
    public void revokeTokenKYCEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.REVOKE_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.REVOKE_KYC.getActualGas());
    }

    @Then("I call estimateGas with RevokeTokenKYC function for NFT")
    public void revokeTokenKYCNonFungibleEstimateGas() {
//        tokenClient.grantKyc(tokenIds.get(1), receiverAccount.getAccountId());
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.REVOKE_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.REVOKE_KYC.getActualGas());
    }

    @Then("I call estimateGas with RevokeTokenKYC function on a token without KYC")
    public void revokeTokenKYCNegativeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.REVOKE_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(6).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with Grant and Revoke KYC nested function")
    public void nestedGrantRevokeKYCEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.NESTED_GRANT_REVOKE_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(2).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));


        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.NESTED_GRANT_REVOKE_KYC.getActualGas());
    }

    @Then("I call estimateGas with Freeze function for fungible token")
    public void freezeFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.FREEZE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.FREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Freeze function for NFT")
    public void freezeNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.FREEZE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.FREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Unfreeze function for fungible token")
    public void unfreezeFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.UNFREEZE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UNFREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Unfreeze function for NFT")
    public void unfreezeNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.UNFREEZE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UNFREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for fungible token")
    public void nestedFreezeAndUnfreezeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.NESTED_FREEZE_UNFREEZE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.NESTED_FREEZE_UNFREEZE.getActualGas());
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for NFT")
    public void nestedFreezeAndUnfreezeNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.NESTED_FREEZE_UNFREEZE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.NESTED_FREEZE_UNFREEZE.getActualGas());
    }


    @Then("I call estimateGas with delete function for Fungible token")
    public void deleteFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DELETE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DELETE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with delete function for NFT")
    public void deleteNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DELETE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DELETE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with delete function for invalid token address")
    public void deleteTokenRandomAddressEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.DELETE_TOKEN_TWICE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(RANDOM_ADDRESS));

        assertContractCallReturnsBadRequest(encodedFunctionCall);
    }

    @Then("I call estimateGas with pause function for fungible token")
    public void pauseFungibleTokenPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.PAUSE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.PAUSE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with pause function for NFT")
    public void pauseNFTPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.PAUSE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.PAUSE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with unpause function for fungible token")
    public void unpauseFungibleTokenPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.UNPAUSE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UNPAUSE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with unpause function for NFT")
    public void unpauseNFTPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.UNPAUSE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UNPAUSE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas for nested pause and unpause function")
    public void pauseUnpauseFungibleTokenNestedCallEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getActualGas());
    }

    @Then("I call estimateGas for nested pause, unpause NFT function")
    public void pauseUnpauseNFTNestedCallEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with updateTokenExpiryInfo function")
    public void updateTokenExpiryInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.UPDATE_TOKEN_EXPIRY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UPDATE_TOKEN_EXPIRY.getActualGas());
    }

    @Then("I call estimateGas with updateTokenInfo function")
    public void updateTokenInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.UPDATE_TOKEN_INFO.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UPDATE_TOKEN_INFO.getActualGas());
    }

    @Then("I call estimateGas with updateTokenKeys function")
    public void updateTokenKeysEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(ContractMethods.UPDATE_TOKEN_KEYS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UPDATE_TOKEN_INFO.getActualGas());
    }

    public void assertContractCallReturnsBadRequest(ByteBuffer encodedFunctionCall) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(estimatePrecompileContractSolidityAddress)
                .estimate(true)
                .build();

        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
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

    private void validateGasEstimation(String selector, int actualGasUsed) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(selector)
                .to(estimatePrecompileContractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse msgSenderResponse =
                mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();

        assertTrue(isWithinDeviation(actualGasUsed, estimatedGas, lowerDeviation, upperDeviation));
    }

    private void createNewToken(
            String symbol, TokenType tokenType, int kycStatus, TokenSupplyType tokenSupplyType, List<CustomFee> customFees) {
        networkTransactionResponse = tokenClient.createToken(
                admin,
                symbol,
                TokenFreezeStatus.Unfrozen_VALUE,
                kycStatus,
                admin,
                10_000_000_000_000L,
                tokenSupplyType,
                10_000_000_000_000L,
                tokenType,
                customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
        tokenIds.add(tokenId);

        verifyToken(tokenId);
    }

    @Retryable(
            value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyToken(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());
    }

    @Retryable(
            value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyNft(TokenId tokenId, Long serialNumber) {
        MirrorNftResponse mirrorNft = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);

        assertNotNull(mirrorNft);
        assertThat(mirrorNft.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(mirrorNft.getSerialNumber()).isEqualTo(serialNumber);
    }

    @Retryable(
            value = {AssertionError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyTx(String txId) {
        MirrorTransactionsResponse txResponse = mirrorClient.getTransactions(txId);
        assertNotNull(txResponse);
    }

    public void createTokenWithCustomFees(int kycStatus) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();

        CustomFixedFee customFixedFee = new CustomFixedFee();
        customFixedFee.setAmount(10);
        customFixedFee.setFeeCollectorAccountId(admin.getAccountId());

        CustomFractionalFee customFractionalFee = new CustomFractionalFee();
        customFractionalFee.setFeeCollectorAccountId(admin.getAccountId());
        customFractionalFee.setNumerator(1);
        customFractionalFee.setDenominator(10);

        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenType.FUNGIBLE_COMMON,
                kycStatus,
                TokenSupplyType.INFINITE,
                List.of(customFixedFee, customFractionalFee));
    }

    public void createNonFungibleTokenWithFixedFee(int kycStatus) {
        CustomFixedFee customFixedFee = new CustomFixedFee();
        customFixedFee.setAmount(10);
        customFixedFee.setFeeCollectorAccountId(admin.getAccountId());

        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenType.NON_FUNGIBLE_UNIQUE,
                kycStatus,
                TokenSupplyType.INFINITE,
                List.of(customFixedFee));
    }

    @Getter
    @RequiredArgsConstructor
    private enum ContractMethods {
        /**
         * 0-expecting a revert
         * 23422-gas estimation is not calculated
         */
        APPROVE("approveExternal", 729571),
        APPROVE_NFT("approveNFTExternal", 729569),
        APPROVE_ERC("approve", 731632),
        ASSOCIATE_TOKEN("associateTokenExternal", 729374),
        ASSOCIATE_TOKENS("associateTokensExternal", 730641),
        BURN_TOKEN("burnTokenExternal", 40247),
        CREATE_FUNGIBLE_TOKEN("createFungibleTokenPublic", 11590398),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES("createFungibleTokenWithCustomFeesPublic", 23422),
        CREATE_NFT("createNonFungibleTokenPublic", 11574020),
        CREATE_NFT_WITH_CUSTOM_FEES("0488c939", 23422),
        CRYPTO_TRANSFER("cryptoTransferExternal", 23422),
        CRYPTO_TRANSFER_HBARS("cryptoTransferExternal", 29698),
        CRYPTO_TRANSFER_NFT("cryptoTransferExternal", 57934),
        DELETE_TOKEN("deleteTokenExternal", 39095),
        DELETE_TOKEN_TWICE("deleteTokenTwiceExternal", 23422),
        DISSOCIATE_AND_ASSOCIATE("dissociateAndAssociateTokenExternal", 1434814),
        DISSOCIATE_TOKEN("dissociateTokenExternal", 729428),
        DISSOCIATE_TOKENS("dissociateTokensExternal", 730641),
        FREEZE_TOKEN("freezeTokenTwiceExternal", 54526),
        GRANT_KYC("grantTokenKycExternal", 39311),
        MINT_TOKEN("mintTokenExternal", 40700),
        MINT_NFT("mintTokenExternal", 732250),
        NESTED_ASSOCIATE("nestedAssociateTokenExternal", 0),
        NESTED_FREEZE_UNFREEZE("nestedFreezeUnfreezeTokenExternal", 54548),
        NESTED_GRANT_REVOKE_KYC("nestedGrantAndRevokeTokenKYCExternal", 54516),
        REVOKE_KYC("revokeTokenKycExternal", 39324),
        SET_APPROVAL_FOR_ALL("setApprovalForAllExternal", 729608),
        TRANSFER_ERC("transfer", 23422),
        TRANSFER_FROM("transferFromExternal", 40822),
        TRANSFER_FROM_ERC("transferFrom", 39511),
        TRANSFER_FROM_NFT("transferFromNFTExternal", 54938),
        TRANSFER_NFT("transferNFTExternal", 53751),
        TRANSFER_NFTS("transferNFTsExternal", 57015),
        TRANSFER_TOKEN("transferTokenExternal", 39666),
        TRANSFER_TOKENS("transferTokensExternal", 42480),
        UNFREEZE_TOKEN("unfreezeTokenExternal", 39323),
        WIPE_TOKEN_ACCOUNT("wipeTokenAccountExternal", 39496),
        WIPE_NFT_ACCOUNT("wipeTokenAccountNFTExternal", 40394),
        PAUSE_TOKEN("pauseTokenExternal", 39112),
        UNPAUSE_TOKEN("unpauseTokenExternal", 39112),
        UPDATE_TOKEN_EXPIRY("updateTokenExpiryInfoExternal", 23422),
        UPDATE_TOKEN_INFO("updateTokenInfoExternal", 23422),
        UPDATE_TOKEN_KEYS("updateTokenKeysExternal", 23422),
        PAUSE_UNPAUSE_NESTED_TOKEN("nestedPauseUnpauseTokenExternal", 54237),;

        private final String functionName;
        private final int actualGas;
    }

    private record DeployedContract(FileId fileId, ContractId contractId,
                                    CompiledSolidityArtifact compiledSolidityArtifact) {
    }
}
