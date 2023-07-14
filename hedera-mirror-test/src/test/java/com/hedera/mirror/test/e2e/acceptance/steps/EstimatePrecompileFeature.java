package com.hedera.mirror.test.e2e.acceptance.steps;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.Strings;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesStringRightPad;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.accountAmount;
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
    private NetworkTransactionResponse networkResponse;
    private DeployedContract deployedContract;
    private ExpandedAccountId receiverAccount;
    private ExpandedAccountId secondReceiverAccount;
    private ExpandedAccountId admin;
    private String contractSolidityAddress;
    private int lowerDeviation;
    private int upperDeviation;
    @Value("classpath:solidity/artifacts/contracts/EstimatePrecompileContract.sol/EstimatePrecompileContract.json")
    private Resource estimatePrecompileTestContract;
    private CompiledSolidityArtifact compiledSolidityArtifacts;
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


    @Given("I create contract with {int} balance")
    public void createNewEstimateContract(int supply) throws IOException {
        try (var in = estimatePrecompileTestContract.getInputStream()) {
            compiledSolidityArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
            createContract(compiledSolidityArtifacts, supply);
        }
        deployedContract = createContract(compiledSolidityArtifacts, supply);
        contractSolidityAddress = deployedContract.contractId().toSolidityAddress();
        newAccountEvmAddress = PrivateKey.generateECDSA().getPublicKey().toEvmAddress().toString();
        admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        receiverAccount = accountClient.createNewECDSAAccount(10);
        secondReceiverAccount = accountClient.createNewECDSAAccount(10);
    }

    @Given("I successfully create and verify a fungible token for estimateGas precompile tests")
    public void createFungibleToken() {
        createTokenWithCustomFees();
    }

    @Given("I successfully create and verify a non fungible token for estimateGas precompile tests")
    public void createNonFungibleToken() {
        createNonFungibleTokenWithFixedFee();
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
        String json = getAbiFunctionAsJsonString(compiledSolidityArtifacts, functionName);
        return Function.fromJson(json);
    }

    @Then("I call estimateGas with associate function for fungible token")
    public void associateFunctionEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("associateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with associate function for NFT")
    public void associateFunctionNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("associateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with dissociate token function without association for fungible token")
    public void dissociateFunctionEstimateGasNegative() {
        //attempt to call dissociate function without having association
        //expecting status 400/revert
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("dissociateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with dissociate token function without association for NFT")
    public void dissociateFunctionNFTEstimateGasNegative() {
        //attempt to call dissociate function without having association
        //expecting status 400/revert
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("dissociateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with nested associate function that executes it twice for fungible token")
    public void nestedAssociateFunctionEstimateGas() {
        //attempt to call associate function twice
        //expecting a revert
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("nestedAssociateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with nested associate function that executes it twice for NFT")
    public void nestedAssociateFunctionNFTEstimateGas() {
        //attempt to call associate function twice
        //expecting a revert
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("nestedAssociateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with dissociate token function for fungible token")
    public void dissociateFunctionEstimateGas() {
        //associating the token with the address
        networkResponse = tokenClient.associate(receiverAccount, tokenIds.get(0));
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("dissociateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with dissociate token function for NFT")
    public void dissociateFunctionNFTEstimateGas() {
        //associating the NFT with the address
        networkResponse = tokenClient.associate(receiverAccount, tokenIds.get(1));
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("dissociateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with dissociate and associate nested function for fungible token")
    public void dissociateAndAssociatedEstimateGas() {
        //token is already associated
        //attempting to execute nested dissociate and associate function
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("dissociateAndAssociateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_AND_ASSOCIATE.getActualGas());
    }

    @Then("I call estimateGas with dissociate and associate nested function for NFT")
    public void dissociateAndAssociatedNFTEstimateGas() {
        //token is already associated
        //attempting to execute nested dissociate and associate function
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("dissociateAndAssociateTokenExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_AND_ASSOCIATE.getActualGas());
    }

    @Then("I call estimateGas with approve function")
    public void approveEstimateGas() {
        validateGasEstimation(
                ContractMethods.APPROVE.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress())
                        + to32BytesString("10"), ContractMethods.APPROVE.getActualGas());
    }

    @Then("I call estimateGas with approveNFT function")
    public void approveNftEstimateGas() {
        validateGasEstimation(
                ContractMethods.APPROVE_NFT.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(String.valueOf(firstNftSerialNumber)), ContractMethods.APPROVE_NFT.getActualGas());
    }

    @Then("I call estimateGas with setApprovalForAll function")
    public void setApprovalForAllEstimateGas() {
        validateGasEstimation(
                ContractMethods.SET_APPROVAL_FOR_ALL.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(String.valueOf(firstNftSerialNumber)), ContractMethods.SET_APPROVAL_FOR_ALL.getActualGas());
    }

    @Then("I call estimateGas with transferFrom function without approval")
    public void transferFromEstimateGasWithoutApproval() {
        validateGasEstimation(
                ContractMethods.TRANSFER_FROM.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress())
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(String.valueOf(5_000_000_000_0L)), ContractMethods.TRANSFER_FROM.getActualGas());
    }

    @Then("I call estimateGas with transferFrom function")
    public void transferFromEstimateGas() {
        tokenClient.associate(receiverAccount, tokenIds.get(0));
        accountClient.approveToken(tokenIds.get(0), receiverAccount.getAccountId(), 10_000_000_000_0L);
        tokenClient.transferFungibleToken(tokenIds.get(0), admin, receiverAccount.getAccountId(), 5_000_000_000_0L);
        validateGasEstimation(
                ContractMethods.TRANSFER_FROM.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress())
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(String.valueOf(5_000_000_000_0L)), ContractMethods.TRANSFER_FROM.getActualGas());
    }

    @Then("I call estimateGas with transferFrom function with more than the approved allowance")
    public void transferFromExceedAllowanceEstimateGas() {
        tokenClient.associate(receiverAccount, tokenIds.get(0));
        accountClient.approveToken(tokenIds.get(0), receiverAccount.getAccountId(), 10_000_000_000L);
        tokenClient.transferFungibleToken(tokenIds.get(0), admin, receiverAccount.getAccountId(), 11_000_000_000_0L);
        validateGasEstimation(
                ContractMethods.TRANSFER_FROM.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress())
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(String.valueOf(55_000_000_000_0L)), ContractMethods.TRANSFER_FROM.getActualGas());
    }

    @Then("I call estimateGas with transferFromNFT function")
    public void transferFromNFTEstimateGas() {
        NftId id = new NftId(tokenIds.get(1), firstNftSerialNumber);
        //already associated line 242
        //tokenClient.associate(receiverAccount, tokenIds.get(1));
//        List<Type> random = Arrays.asList(new String(tokenIds.get(1).toSolidityAddress()),
//                admin.getAccountId().toSolidityAddress(),
//                receiverAccount.getAccountId().toSolidityAddress(),
//                Long.toString(firstNftSerialNumber));


        accountClient.approveNft(id, receiverAccount.getAccountId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(tokenIds.get(1), admin, receiverAccount.getAccountId(), Collections.singletonList(firstNftSerialNumber));
        validateGasEstimation(
                ContractMethods.TRANSFER_FROM_NFT.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress())
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(String.valueOf(firstNftSerialNumber)), ContractMethods.TRANSFER_FROM_NFT.getActualGas());
    }

    @Then("I call estimateGas with transferFromNFT with invalid serial number")
    public void transferFromNFTInvalidSerialEstimateGas() {
        NftId id = new NftId(tokenIds.get(1), firstNftSerialNumber);
        tokenClient.associate(receiverAccount, tokenIds.get(1));
        accountClient.approveNft(id, receiverAccount.getAccountId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(tokenIds.get(1), admin, receiverAccount.getAccountId(), Collections.singletonList(firstNftSerialNumber));
        validateGasEstimation(
                ContractMethods.TRANSFER_FROM_NFT.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress())
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString("1111"), ContractMethods.TRANSFER_FROM_NFT.getActualGas());
    }

    @Then("I call estimateGas with transferToken function")
    public void transferTokenEstimateGas() {
        validateGasEstimation(
                ContractMethods.TRANSFER_TOKEN.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress())
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString("5"), ContractMethods.TRANSFER_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with transferNFT function")
    public void transferNFTEstimateGas() {
        validateGasEstimation(
                ContractMethods.TRANSFER_NFT.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress())
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(String.valueOf(firstNftSerialNumber)), ContractMethods.TRANSFER_NFT.getActualGas());
    }

    @Then("I create 2 more fungible tokens")
    public void createAnotherFungibleToken() {
        for (int i = 0; i < 2; i++) {
            createTokenWithCustomFees();
        }
    }

    @Then("I create 2 more NFTs")
    public void createAnotherNFT() {
        for (int i = 0; i < 2; i++) {
            createNonFungibleTokenWithFixedFee();
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
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("associateTokensExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddressArray(Arrays.asList(tokenIds.get(2).toSolidityAddress(), tokenIds.get(3).toSolidityAddress())));
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with associateTokens function for NFTs")
    public void associateNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("associateTokensExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddressArray(Arrays.asList(tokenIds.get(4).toSolidityAddress(), tokenIds.get(5).toSolidityAddress())));
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with dissociateTokens function for fungible tokens")
    public void dissociateTokensEstimateGas() {
        //associating tokens with the address
        tokenClient.associate(receiverAccount, tokenIds.get(2));
        networkResponse = tokenClient.associate(receiverAccount, tokenIds.get(3));
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("dissociateTokensExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddressArray(Arrays.asList(tokenIds.get(2).toSolidityAddress(), tokenIds.get(3).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with dissociateTokens function for NFTs")
    public void dissociateNFTEstimateGas() {
        //associating tokens with the address
        tokenClient.associate(receiverAccount, tokenIds.get(4));
        networkResponse = tokenClient.associate(receiverAccount, tokenIds.get(5));
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("dissociateTokensExternal").encodeCallWithArgs(
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asHeadlongAddressArray(Arrays.asList(tokenIds.get(4).toSolidityAddress(), tokenIds.get(5).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with transferTokens function for fungible tokens")
    public void transferTokensEstimateGas() {
        //represents the number -10 (Two's compliment)
        String spendingValue = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff6";
        // The string being constructed represents the function's input in a format that the Ethereum Virtual Machine (EVM) understands.
        StringBuilder requestData = new StringBuilder();
        requestData.append(ContractMethods.TRANSFER_TOKENS.getSelector())
                .append(to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .append(to32BytesString("60"))  // Offset for the 'accountIds' array data in the requestData string.
                .append(to32BytesString("e0"))  // Offset for the 'amounts' array data in the requestData string.
                .append(to32BytesString("3"))  // Length of 'accountIds' array: 3.
                .append(to32BytesString(admin.getAccountId().toSolidityAddress()))  // Admin's address in the 'accountIds' array.
                .append(to32BytesString(receiverAccount.getAccountId().toSolidityAddress()))  // Receiver's address in the 'accountIds' array.
                .append(to32BytesString(secondReceiverAccount.getAccountId().toSolidityAddress()))  // Second receiver's address in the 'accountIds' array.
                .append(to32BytesString("3"))  // Length of 'amounts' array: 3.
                .append(spendingValue)  // Amount transferred from the admin (-10 tokens).
                .append(to32BytesString("5"))  // Amount received by the receiver (5 tokens).
                .append(to32BytesString("5"));  // Amount received by the second receiver (5 tokens).

        validateGasEstimation(requestData.toString(), ContractMethods.TRANSFER_TOKENS.getActualGas());
    }

    @Then("I call estimateGas with transferTokens function for nfts")
    public void transferNFTsEstimateGas() {
        NetworkTransactionResponse tx = tokenClient.mint(tokenIds.get(5), RandomUtils.nextBytes(4));
        assertNotNull(tx.getTransactionId());

        tokenClient.associate(receiverAccount, tokenIds.get(5));
        tokenClient.associate(secondReceiverAccount, tokenIds.get(5));

        StringBuilder requestData = new StringBuilder();
        requestData.append(ContractMethods.TRANSFER_NFTS.getSelector())
                .append(to32BytesString(tokenIds.get(5).toSolidityAddress()))
                .append(to32BytesString("80"))  // Offset for the 'sender' array data.
                .append(to32BytesString("c0"))  // Offset for the 'receiver' array data.
                .append(to32BytesString("120"))  // Offset for the 'serialNumber' array data.
                .append(to32BytesString("1"))  // Length of 'sender' array: 1.
                .append(to32BytesString(admin.getAccountId().toSolidityAddress()))
                .append(to32BytesString("2"))  // Length of 'receiver' array: 2.
                .append(to32BytesString(receiverAccount.getAccountId().toSolidityAddress()))  // First receiver's address in the 'receiver' array.
                .append(to32BytesString(secondReceiverAccount.getAccountId().toSolidityAddress()))  // Second receiver's address in the 'receiver' array.
                .append(to32BytesString("2"))  // Length of 'serialNumber' array: 2.
                .append(to32BytesString("1"))  // First serial number in the 'serialNumber' array: 1.
                .append(to32BytesString("2"));  // Second serial number in the 'serialNumber' array: 2.

        validateGasEstimation(requestData.toString(), ContractMethods.TRANSFER_NFTS.getActualGas());
    }

    @Then("I call estimateGas with cryptoTransfer function for hbars")
    public void cryptoTransferHbarEstimateGas() {
        //amount equals to -10 (Two's compliment)
        String spendingValue = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff6";

        StringBuilder requestData = new StringBuilder();
        requestData.append(ContractMethods.CRYPTO_TRANSFER.getSelector())
                .append(to32BytesString("40")) // Offset for the 'transferList' parameter
                .append(to32BytesString("140")) // Offset for the 'tokenTransferList' parameter
                .append(to32BytesString("20")) // Length of the 'transferList' array
                .append(to32BytesString("2")) // Number of elements in the 'transferList' array
                .append(to32BytesString(admin.getAccountId().toSolidityAddress())) // AccountID of the first element in 'transferList'
                .append(spendingValue)
                .append(to32BytesString("0"))
                .append(to32BytesString(receiverAccount.getAccountId().toSolidityAddress())) // AccountID of the second element in 'transferList'
                .append(to32BytesString("a")) // Amount of the second element in 'transferList' (10 in decimal)
                .append(to32BytesString("0"))
                .append(to32BytesString("0")); // Length of 'tokenTransferList' array

        validateGasEstimation(requestData.toString(), ContractMethods.CRYPTO_TRANSFER.getActualGas());
    }

    private TokenTransferListBuilder tokenTransferList() {
        return new TokenTransferListBuilder();
    }

    @Then("I call estimateGas with cryptoTransfer function for nft")
    public void cryptoTransferNFTEstimateGas() {
        StringBuilder requestData = new StringBuilder();
        requestData.append(ContractMethods.CRYPTO_TRANSFER.getSelector())
                .append(to32BytesString("40")) // Offset to start of 'cryptoTransfers' struct data
                .append(to32BytesString("80")) // Offset to start of 'tokenTransferList' array data
                .append(to32BytesString("20")) // Length of 'cryptoTransfers.transfers' array
                .append(to32BytesString("0")) // Length of 'cryptoTransfers.nftTransfers' array
                .append(to32BytesString("1")) // Length of 'tokenTransferList' array: 1
                .append(to32BytesString("20")) // Offset to start of 'tokenTransferList[0]' struct data
                .append(to32BytesString(tokenIds.get(5).toSolidityAddress())) // 'tokenTransferList[0].token' address value
                .append(to32BytesString("60")) // Offset to start of 'tokenTransferList[0].transfers' array data
                .append(to32BytesString("80")) // Offset to start of 'tokenTransferList[0].nftTransfers' array data
                .append(to32BytesString("0")) // Length of 'tokenTransferList[0].transfers' array (empty in this case)
                .append(to32BytesString("1")) // Length of 'tokenTransferList[0].nftTransfers' array: 1
                .append(to32BytesString(admin.getAccountId().toSolidityAddress())) // 'tokenTransferList[0].nftTransfers[0].senderAccountID' address value
                .append(to32BytesString(receiverAccount.getAccountId().toSolidityAddress())) // 'tokenTransferList[0].nftTransfers[0].receiverAccountID' address value
                .append(to32BytesString("2")) // 'tokenTransferList[0].nftTransfers[0].serialNumber' value
                .append(to32BytesString("0"));

        validateGasEstimation(requestData.toString(), ContractMethods.CRYPTO_TRANSFER.getActualGas());
    }

    @Then("I call estimateGas with cryptoTransfer function for fungible tokens")
    public void cryptoTransferFungibleEstimateGas() {
        var json = getAbiFunctionAsJsonString(compiledSolidityArtifacts, "cryptoTransferExternal");
        Function f2 = Function.fromJson(json);
//        var args1 = (Object) new Tuple[]{tokenTransferList()
//                .withAccountAmounts(Tuple.EMPTY)
//                .build()};
        var args2 = (Object) new Tuple[]{tokenTransferList()
                .forToken(tokenIds.get(0).toSolidityAddress())
                .withAccountAmounts(
                        accountAmount(admin.getAccountId().toSolidityAddress(), -5L, false),
                        accountAmount(receiverAccount.getAccountId().toSolidityAddress(), 5L, false))
                .build()};
        ByteBuffer two = f2.encodeCallWithArgs(Tuple.of((Object) EMPTY_TUPLE_ARRAY), args2);
        var headLongBody2 = Strings.encode(two);


        //amount equals to -5 (Two's compliment)
        String spendingValue = "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffb";

        StringBuilder requestData = new StringBuilder();
        requestData.append(ContractMethods.CRYPTO_TRANSFER.getSelector())
                .append(to32BytesString("40")) // Offset to start of 'cryptoTransfers' struct data
                .append(to32BytesString("80")) // Offset to start of 'tokenTransferList' array data
                .append(to32BytesString("20")) // Length of 'cryptoTransfers.transfers' array
                .append(to32BytesString("0")) // Length of 'cryptoTransfers.nftTransfers' array
                .append(to32BytesString("1")) // Length of 'tokenTransferList' array: 1
                .append(to32BytesString("20")) // Offset to start of 'tokenTransferList[0]' struct data
                .append(to32BytesString(tokenIds.get(0).toSolidityAddress())) // 'tokenTransferList[0].token' address value
                .append(to32BytesString("60")) // Offset to start of 'tokenTransferList[0].transfers' array data
                .append(to32BytesString("140")) // Offset to start of 'tokenTransferList[0].nftTransfers' array data (empty in this case)
                .append(to32BytesString("2")) // Length of 'tokenTransferList[0].transfers' array: 2
                .append(to32BytesString(receiverAccount.getAccountId().toSolidityAddress())) // 'tokenTransferList[0].transfers[0].accountID' address value
                .append(to32BytesString("5")) // 'tokenTransferList[0].transfers[0].amount' value
                .append(to32BytesString("0")) // Length of 'tokenTransferList[0].transfers[0].serialNumbers' array
                .append(to32BytesString(admin.getAccountId().toSolidityAddress())) // 'tokenTransferList[0].transfers[1].accountID' address value
                .append(spendingValue) // 'tokenTransferList[0].transfers[1].amount' value (-5)
                .append(to32BytesString("0")) // Length of 'tokenTransferList[0].transfers[1].serialNumbers' array
                .append(to32BytesString("0")); // Length of 'tokenTransferList[0].nftTransfers' array (empty in this case)

        validateGasEstimation(requestData.toString(), ContractMethods.CRYPTO_TRANSFER.getActualGas());
    }

    @Then("I call estimateGas with mintToken function for fungible token")
    public void mintFungibleTokenEstimateGas() {
        StringBuilder requestData = new StringBuilder();
        requestData.append(ContractMethods.MINT_TOKEN.getSelector())
                .append(to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .append(to32BytesString("1"))  // The 'amount' parameter: 1
                .append(to32BytesString("60")) // Offset for the start of 'metadata' array data
                .append(to32BytesString("0")); // Length of 'metadata' array: 0

        validateGasEstimation(requestData.toString(), ContractMethods.MINT_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with mintToken function for NFT")
    public void mintNFTEstimateGas() {
        StringBuilder requestData = new StringBuilder();
        requestData.append(ContractMethods.MINT_TOKEN.getSelector())
                .append(to32BytesString(tokenIds.get(1).toSolidityAddress()))
                .append(to32BytesString("1")) // The 'amount' parameter: 0
                .append(to32BytesString("60")) // Offset for the start of 'metadata' array data
                .append(to32BytesString("1")) // Length of 'metadata' array: 1
                .append(to32BytesString("20")) // Offset for the first element in the 'metadata' array
                .append(to32BytesString("1")) // Length of the first element in 'metadata' array: 1
                .append(to32BytesStringRightPad("02")); // First (and only) element in 'metadata' array: '0x02'

        validateGasEstimation(requestData.toString(), ContractMethods.MINT_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with burnToken function for fungible token")
    public void burnFungibleTokenEstimateGas() {
        StringBuilder requestData = new StringBuilder();
        requestData.append(ContractMethods.BURN_TOKEN.getSelector())
                .append(to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .append(to32BytesString("1")) // The 'amount' parameter: 1
                .append(to32BytesString("60")) // Offset for the start of 'serialNumbers' array data
                .append(to32BytesString("0")); // Length of 'serialNumbers' array: 0

        validateGasEstimation(requestData.toString(), ContractMethods.BURN_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with burnToken function for NFT")
    public void burnNFTEstimateGas() {
        StringBuilder requestData = new StringBuilder();
        requestData.append(ContractMethods.BURN_TOKEN.getSelector())
                .append(to32BytesString(tokenIds.get(1).toSolidityAddress()))
                .append(to32BytesString("0")) // The 'amount' parameter: 0 (in hexadecimal)
                .append(to32BytesString("60")) // Offset for the start of 'serialNumbers' array data
                .append(to32BytesString("1")) // Length of 'serialNumbers' array: 1 (in hexadecimal)
                .append(to32BytesString("1")); // First (and only) element in 'serialNumbers' array: 1 (in hexadecimal)

        validateGasEstimation(requestData.toString(), ContractMethods.BURN_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with CreateFungibleToken function")
    public void createFungibleTokenEstimateGas() {
        validateGasEstimation(ContractMethods.CREATE_FUNGIBLE_TOKEN.getSelector()
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.CREATE_FUNGIBLE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with CreateNFT function")
    public void createNFTEstimateGas() {
        validateGasEstimation(ContractMethods.CREATE_NFT.getSelector()
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.CREATE_NFT.getActualGas());
    }

    @Then("I call estimateGas with CreateFungibleToken function with custom fees")
    public void createFungibleTokenWithCustomFeesEstimateGas() {
        validateGasEstimation(ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getSelector()
                + to32BytesString(admin.getAccountId().toSolidityAddress())
                + to32BytesString(tokenIds.get(0).toSolidityAddress()), ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getActualGas());
    }

    @Then("I call estimateGas with CreateNFT function with custom fees")
    public void createNFTWithCustomFeesEstimateGas() {
        validateGasEstimation(ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES.getSelector()
                + to32BytesString(admin.getAccountId().toSolidityAddress())
                + to32BytesString(tokenIds.get(0).toSolidityAddress()), ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES.getActualGas());
    }

    @Then("I call estimateGas with WipeTokenAccount function")
    public void wipeTokenAccountEstimateGas() {
        tokenClient.grantKyc(tokenIds.get(0), receiverAccount.getAccountId());
        accountClient.approveToken(tokenIds.get(0), receiverAccount.getAccountId(), 10_000_000_000L);
        networkResponse = tokenClient.transferFungibleToken(tokenIds.get(0), admin, receiverAccount.getAccountId(), 3_000_000_000_0L);
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("wipeTokenAccountExternal").encodeCallWithArgs(
                asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                1L);

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.WIPE_TOKEN_ACCOUNT.getActualGas());
//        //reverting the KYC change
//        networkResponse = tokenClient.revokeKyc(tokenIds.get(0), admin.getAccountId());
//        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());
    }

    @Then("I call estimateGas with WipeTokenAccount function with invalid amount")
    public void wipeTokenAccountInvalidAmountEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("wipeTokenAccountExternal").encodeCallWithArgs(
                asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                100000000000000000L);

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with WipeNFTAccount function")
    public void wipeNFTAccountEstimateGas() {
        tokenClient.grantKyc(tokenIds.get(1), receiverAccount.getAccountId());
        NftId id = new NftId(tokenIds.get(1), firstNftSerialNumber);
        accountClient.approveNft(id, receiverAccount.getAccountId());
        networkResponse = tokenClient.transferNonFungibleToken(tokenIds.get(1), admin, receiverAccount.getAccountId(), Collections.singletonList(firstNftSerialNumber));
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("wipeTokenAccountNFTExternal").encodeCallWithArgs(
                asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asLongArray(List.of(1L)));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.WIPE_NFT_ACCOUNT.getActualGas());
//        //reverting the KYC change
//        tokenClient.revokeKyc(tokenIds.get(1), admin.getAccountId());
    }

    @Then("I call estimateGas with WipeNFTAccount function with invalid serial number")
    public void wipeNFTAccountInvalidSerialNumberEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact("wipeTokenAccountNFTExternal").encodeCallWithArgs(
                asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                asLongArray(List.of(66L)));

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with GrantKYC function for fungible token")
    public void grantKYCFungibleEstimateGas() {
        validateGasEstimation(ContractMethods.GRANT_KYC.getSelector()
                + to32BytesString(tokenIds.get(0).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.GRANT_KYC.getActualGas());
    }

    @Then("I call estimateGas with GrantKYC function for NFT")
    public void grantKYCNonFungibleEstimateGas() {
        validateGasEstimation(ContractMethods.GRANT_KYC.getSelector()
                + to32BytesString(tokenIds.get(1).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.GRANT_KYC.getActualGas());
    }

    @Then("I call estimateGas with GrantKYC function on a token with KYC")
    public void grantKYCNegativeEstimateGas() {
        //granting KYC so it can be wiped
        //expecting a revert
        tokenClient.grantKyc(tokenIds.get(1), admin.getAccountId());
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.GRANT_KYC.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
        //reverting the KYC change
        tokenClient.revokeKyc(tokenIds.get(1), admin.getAccountId());
    }

    @Then("I call estimateGas with RevokeTokenKYC function for fungible token")
    public void revokeTokenKYCEstimateGas() {
        tokenClient.grantKyc(tokenIds.get(0), admin.getAccountId());
        validateGasEstimation(ContractMethods.REVOKE_KYC.getSelector()
                + to32BytesString(tokenIds.get(0).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.REVOKE_KYC.getActualGas());
    }

    @Then("I call estimateGas with RevokeTokenKYC function for NFT")
    public void revokeTokenKYCNonFungibleEstimateGas() {
        tokenClient.grantKyc(tokenIds.get(1), admin.getAccountId());
        validateGasEstimation(ContractMethods.REVOKE_KYC.getSelector()
                + to32BytesString(tokenIds.get(1).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.REVOKE_KYC.getActualGas());
    }

    @Then("I call estimateGas with RevokeTokenKYC function on a token without KYC")
    public void revokeTokenKYCNegativeEstimateGas() {
        //reverting the KYC change
        tokenClient.revokeKyc(tokenIds.get(1), admin.getAccountId());
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.REVOKE_KYC.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with RevokeTokenKYC function twice")
    public void revokeTokenKYCTwiceEstimateGas() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.REVOKE_KYC_TWICE.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with GrantKYC function twice")
    public void grantTokenKYCTwiceEstimateGas() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.GRANT_KYC_TWICE.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with Grant and Revoke KYC nested function")
    public void nestedGrantRevokeKYCEstimateGas() {
        validateGasEstimation(ContractMethods.NESTED_GRANT_REVOKE_KYC.getSelector()
                + to32BytesString(tokenIds.get(1).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.NESTED_GRANT_REVOKE_KYC.getActualGas());
    }

    @Then("I call estimateGas with Freeze function for fungible token")
    public void freezeFungibleEstimateGas() {
        validateGasEstimation(ContractMethods.FREEZE_TOKEN.getSelector()
                + to32BytesString(tokenIds.get(0).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.FREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Freeze function for NFT")
    public void freezeNonFungibleEstimateGas() {
        validateGasEstimation(ContractMethods.FREEZE_TOKEN.getSelector()
                + to32BytesString(tokenIds.get(1).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.FREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Unfreeze function for fungible token")
    public void unfreezeFungibleEstimateGas() {
        networkResponse = tokenClient.freeze(tokenIds.get(0), admin.getAccountId());
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());
        validateGasEstimation(ContractMethods.UNFREEZE_TOKEN.getSelector()
                + to32BytesString(tokenIds.get(0).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.UNFREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Unfreeze function for NFT")
    public void unfreezeNonFungibleEstimateGas() {
        networkResponse = tokenClient.freeze(tokenIds.get(1), admin.getAccountId());
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());
        validateGasEstimation(ContractMethods.UNFREEZE_TOKEN.getSelector()
                + to32BytesString(tokenIds.get(1).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.UNFREEZE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with Freeze function on a fungible token with frozen status")
    public void freezeAlreadyFrozenTokenEstimateGas() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.FREEZE_TOKEN.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with Freeze function on a NFT with frozen status")
    public void freezeAlreadyFrozenTNFTEstimateGas() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.FREEZE_TOKEN.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with Freeze function twice for fungible token")
    public void freezeFunctionTwiceEstimateGas() {
        networkResponse = tokenClient.unfreeze(tokenIds.get(0), admin.getAccountId());
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.FREEZE_TOKEN_TWICE.getSelector()
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with Freeze function twice for NFT")
    public void freezeFunctionTwiceNFTEstimateGas() {
        networkResponse = tokenClient.unfreeze(tokenIds.get(1), admin.getAccountId());
        verifyTx(networkResponse.getTransactionIdStringNoCheckSum());
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.FREEZE_TOKEN_TWICE.getSelector()
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(admin.getAccountId().toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for fungible token")
    public void nestedFreezeAndUnfreezeEstimateGas() {
        validateGasEstimation(ContractMethods.NESTED_FREEZE_UNFREEZE.getSelector()
                + to32BytesString(tokenIds.get(0).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.NESTED_FREEZE_UNFREEZE.getActualGas());
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for NFT")
    public void nestedFreezeAndUnfreezeNFTEstimateGas() {
        validateGasEstimation(ContractMethods.NESTED_FREEZE_UNFREEZE.getSelector()
                + to32BytesString(tokenIds.get(1).toSolidityAddress())
                + to32BytesString(admin.getAccountId().toSolidityAddress()), ContractMethods.NESTED_FREEZE_UNFREEZE.getActualGas());
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
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse msgSenderResponse =
                mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();

        assertTrue(isWithinDeviation(actualGasUsed, estimatedGas, lowerDeviation, upperDeviation));
    }

    private void createNewToken(
            String symbol, TokenType tokenType, TokenSupplyType tokenSupplyType, List<CustomFee> customFees) {
        networkTransactionResponse = tokenClient.createToken(
                admin,
                symbol,
                TokenFreezeStatus.Unfrozen_VALUE,
                1,
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

    public void createTokenWithCustomFees() {
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
                TokenSupplyType.INFINITE,
                List.of(customFixedFee, customFractionalFee));
    }

    public void createNonFungibleTokenWithFixedFee() {
        CustomFixedFee customFixedFee = new CustomFixedFee();
        customFixedFee.setAmount(10);
        customFixedFee.setFeeCollectorAccountId(admin.getAccountId());

        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenType.NON_FUNGIBLE_UNIQUE,
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
        APPROVE("ceda64c4", 23422),
        APPROVE_NFT("84d232f5", 23422),
        ASSOCIATE_TOKEN("d91cfc95", 729374),
        ASSOCIATE_TOKENS("2fc358f7", 730641),
        BURN_TOKEN("632da116", 40247),
        CREATE_FUNGIBLE_TOKEN("25a92819", 23422),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES("8ba74da0", 23422),
        CREATE_NFT("d85f74c1", 23422),
        CREATE_NFT_WITH_CUSTOM_FEES("0488c939", 23422),
        CRYPTO_TRANSFER("a6218810", 23422),
        DISSOCIATE_AND_ASSOCIATE("f1938266", 1434814),
        DISSOCIATE_TOKEN("9c219247", 729428),
        DISSOCIATE_TOKENS("2390c1fa", 730641),
        FREEZE_TOKEN("9333700b", 23422),
        FREEZE_TOKEN_TWICE("17f96caa", 0),
        GRANT_KYC("ec4ca639", 23422),
        GRANT_KYC_TWICE("15533c95", 0),
        MINT_TOKEN("0c0295d4", 40700),
        NESTED_ASSOCIATE("437dffd5", 0),
        NESTED_FREEZE_UNFREEZE("901fe3d0", 23422),
        NESTED_GRANT_REVOKE_KYC("06a1a5d3", 23422),
        REVOKE_KYC("52205b33", 23422),
        REVOKE_KYC_TWICE("282418a3", 0),
        SET_APPROVAL_FOR_ALL("e31b839c", 23422),
        TRANSFER_FROM("75a85472", 23422),
        TRANSFER_FROM_NFT("9bc4d354", 23422),
        TRANSFER_NFT("bafa6a91", 23422),
        TRANSFER_NFTS("1ba978fc", 23422),
        TRANSFER_TOKEN("4fd6ce0a", 23422),
        TRANSFER_TOKENS("aa835c63", 23422),
        UNFREEZE_TOKEN("e95a71e5", 23422),
        WIPE_TOKEN_ACCOUNT("5ac4fef8", 39496),
        WIPE_NFT_ACCOUNT("4c09c804", 40394);

        private final String selector;
        private final int actualGas;
    }

    private record DeployedContract(FileId fileId, ContractId contractId,
                                    CompiledSolidityArtifact compiledSolidityArtifact) {
    }
}
