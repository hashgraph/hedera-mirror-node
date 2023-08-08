package com.hedera.mirror.test.e2e.acceptance.steps;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.Strings;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.NftId;
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
    private DeployedContract deployedErcTestContract;
    private DeployedContract deployedPrecompileContract;
    private ExpandedAccountId receiverAccount;
    private ExpandedAccountId secondReceiverAccount;
    private ExpandedAccountId admin;
    private String estimatePrecompileContractSolidityAddress;
    private String ercTestContractSolidityAddress;
    private String precompileTestContractSolidityAddress;
    private int lowerDeviation;
    private int upperDeviation;
    @Value("classpath:solidity/artifacts/contracts/EstimatePrecompileContract.sol/EstimatePrecompileContract.json")
    private Resource estimatePrecompileTestContract;

    @Value("classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json")
    private Resource ercTestContract;

    @Value("classpath:solidity/artifacts/contracts/PrecompileTestContract.sol/PrecompileTestContract.json")
    private Resource precompileTestContract;
    private CompiledSolidityArtifact compiledEstimatePrecompileSolidityArtifacts;
    private CompiledSolidityArtifact compiledErcTestContractSolidityArtifacts;
    private CompiledSolidityArtifact compiledPrecompileContractSolidityArtifacts;

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
        deployedErcTestContract = createContract(compiledErcTestContractSolidityArtifacts, supply);
        ercTestContractSolidityAddress = deployedErcTestContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create Precompile contract with {int} balance")
    public void createNewPrecompileTestContract(int supply) throws IOException {
        try (var in = precompileTestContract.getInputStream()) {
            compiledPrecompileContractSolidityArtifacts = MAPPER.readValue(in, CompiledSolidityArtifact.class);
        }
        deployedPrecompileContract = createContract(compiledPrecompileContractSolidityArtifacts, 0);
        precompileTestContractSolidityAddress = deployedPrecompileContract.contractId().toSolidityAddress();
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

    @Then("I call estimateGas with associate function for fungible token")
    public void associateFunctionEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.ASSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with associate function for NFT")
    public void associateFunctionNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.ASSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate token function without association for fungible token")
    public void dissociateFunctionEstimateGasNegative() {
        //attempt to call dissociate function without having association
        //expecting status 400/revert
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.DISSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate token function without association for NFT")
    public void dissociateFunctionNFTEstimateGasNegative() {
        //attempt to call dissociate function without having association
        //expecting status 400/revert
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.DISSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for fungible token")
    public void nestedAssociateFunctionEstimateGas() {
        //attempt to call associate function twice
        //expecting a revert
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.NESTED_ASSOCIATE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested associate function that executes it twice for NFT")
    public void nestedAssociateFunctionNFTEstimateGas() {
        //attempt to call associate function twice
        //expecting a revert
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.NESTED_ASSOCIATE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate token function for fungible token")
    public void dissociateFunctionEstimateGas() {
        //associating the token with the address
        networkTransactionResponse = tokenClient.associate(receiverAccount, tokenIds.get(0));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.DISSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate token function for NFT")
    public void dissociateFunctionNFTEstimateGas() {
        //associating the NFT with the address
        networkTransactionResponse = tokenClient.associate(receiverAccount, tokenIds.get(1));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.DISSOCIATE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate and associate nested function for fungible token")
    public void dissociateAndAssociatedEstimateGas() {
        //token is already associated
        //attempting to execute nested dissociate and associate function
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.DISSOCIATE_AND_ASSOCIATE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.DISSOCIATE_AND_ASSOCIATE.getActualGas(), estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociate and associate nested function for NFT")
    public void dissociateAndAssociatedNFTEstimateGas() {
        //token is already associated
        //attempting to execute nested dissociate and associate function
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.DISSOCIATE_AND_ASSOCIATE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.DISSOCIATE_AND_ASSOCIATE.getActualGas(), estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with approve function without association")
    public void approveWithoutAssociationEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.APPROVE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("10"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with setApprovalForAll function without association")
    public void setApprovalForAllWithoutAssociationEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.SET_APPROVAL_FOR_ALL.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        true);

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with approveNFT function without association")
    public void approveNonFungibleWithoutAssociationEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.APPROVE_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("1"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I associate contracts with the tokens and approve the all nft serials")
    public void associateTokensWithContract() throws InvalidProtocolBufferException {
        //In order to execute Approve, approveNFT, ercApprove we need to associate the contract with the token
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), tokenIds.get(0));
        tokenClient.associate(deployedEstimatePrecompileContract.contractId(), tokenIds.get(1));
        tokenClient.associate(deployedErcTestContract.contractId(), tokenIds.get(0));
        //approve is also needed for the approveNFT function
        networkTransactionResponse = accountClient.approveNftAllSerials(tokenIds.get(1),
                deployedEstimatePrecompileContract.contractId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
    }

    @Then("I call estimateGas with approve function")
    public void approveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.APPROVE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("10"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.APPROVE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with approveNFT function")
    public void approveNftEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.APPROVE_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("1"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.APPROVE_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC approve function")
    public void ercApproveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.APPROVE_ERC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("10"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.APPROVE_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with setApprovalForAll function")
    public void setApprovalForAllEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.SET_APPROVAL_FOR_ALL.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        true);

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.SET_APPROVAL_FOR_ALL.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFrom function without approval")
    public void transferFromEstimateGasWithoutApproval() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.TRANSFER_FROM.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("5"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
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

        assertContractCallReturnsBadRequest(encodedFunctionCall, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFrom function")
    public void transferFromEstimateGas() {
        networkTransactionResponse = tokenClient.grantKyc(tokenIds.get(0), receiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        networkTransactionResponse = accountClient.approveToken(tokenIds.get(0), receiverAccount.getAccountId(), 10);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.TRANSFER_FROM.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("5"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_FROM.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transferFrom function")
    public void ercTransferFromEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TRANSFER_FROM_ERC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("5"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_FROM_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFrom function with more than the approved allowance")
    public void transferFromExceedAllowanceEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.TRANSFER_FROM.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("500"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transferFrom function with more than the approved allowance")
    public void ercTransferFromExceedsAllowanceEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TRANSFER_FROM_ERC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("500"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFromNFT function")
    public void transferFromNFTEstimateGas() {
        NftId id = new NftId(tokenIds.get(1), firstNftSerialNumber);

        networkTransactionResponse = accountClient.approveNft(id, receiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        networkTransactionResponse = tokenClient.grantKyc(tokenIds.get(1), receiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        //tokenClient.transferNonFungibleToken(tokenIds.get(1), admin, receiverAccount.getAccountId(), Collections.singletonList(firstNftSerialNumber));

        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.TRANSFER_FROM_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("1"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_FROM_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferFromNFT with invalid serial number")
    public void transferFromNFTInvalidSerialEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.TRANSFER_FROM_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("50"));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferToken function")
    public void transferTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.TRANSFER_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        5L);

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferNFT function")
    public void transferNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.TRANSFER_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        1L);

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC transfer function")
    public void ercTransferEstimateGas() throws InvalidProtocolBufferException {
        accountClient.approveToken(tokenIds.get(0), receiverAccount.getAccountId(), 50L);
        tokenClient.grantKyc(tokenIds.get(0), deployedErcTestContract.contractId());
        networkTransactionResponse = tokenClient.transferFungibleToken(tokenIds.get(0), admin,
                AccountId.fromString(deployedErcTestContract.contractId().toString()), 10);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(ContractMethods.TRANSFER_ERC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        new BigInteger("5"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_ERC.getActualGas(),
                ercTestContractSolidityAddress);
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
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.ASSOCIATE_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(tokenIds.get(2).toSolidityAddress(),
                                tokenIds.get(3).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with associateTokens function for NFTs")
    public void associateNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.ASSOCIATE_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(tokenIds.get(4).toSolidityAddress(),
                                tokenIds.get(5).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ASSOCIATE_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociateTokens function for fungible tokens")
    public void dissociateTokensEstimateGas() {
        //associating tokens with the address
        tokenClient.associate(receiverAccount, tokenIds.get(2));
        networkTransactionResponse = tokenClient.associate(receiverAccount, tokenIds.get(3));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.DISSOCIATE_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(tokenIds.get(2).toSolidityAddress(),
                                tokenIds.get(3).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with dissociateTokens function for NFTs")
    public void dissociateNFTEstimateGas() {
        //associating tokens with the address
        tokenClient.associate(receiverAccount, tokenIds.get(4));
        networkTransactionResponse = tokenClient.associate(receiverAccount, tokenIds.get(5));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.DISSOCIATE_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(tokenIds.get(4).toSolidityAddress(),
                                tokenIds.get(5).toSolidityAddress())));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DISSOCIATE_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with transferTokens function")
    public void transferTokensEstimateGas() {
        tokenClient.associate(secondReceiverAccount, tokenIds.get(0));
        networkTransactionResponse = tokenClient.grantKyc(tokenIds.get(0), secondReceiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        networkTransactionResponse = accountClient.approveToken(tokenIds.get(0), secondReceiverAccount.getAccountId(),
                10);
        verifyMirrorTransactionsResponse(mirrorClient, 200);
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.TRANSFER_TOKENS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(admin.getAccountId().toSolidityAddress(),
                                receiverAccount.getAccountId().toSolidityAddress(),
                                secondReceiverAccount.getAccountId().toSolidityAddress())),
                        new long[]{-6L, 3L, 3L});

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_TOKENS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
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
        networkTransactionResponse = accountClient.approveNftAllSerials(tokenIds.get(5),
                secondReceiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.TRANSFER_NFTS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(5).toSolidityAddress()),
                        asHeadlongAddressArray(Arrays.asList(admin.getAccountId().toSolidityAddress())),
                        asHeadlongAddressArray(Arrays.asList(receiverAccount.getAccountId().toSolidityAddress(),
                                secondReceiverAccount.getAccountId().toSolidityAddress())),
                        new long[]{1, 2});

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TRANSFER_NFTS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with cryptoTransfer function for hbars")
    public void cryptoTransferHbarEstimateGas() {
        var senderTransfer = accountAmount(admin.getAccountId().toSolidityAddress(), -10L, false);
        var receiverTransfer = accountAmount(receiverAccount.getAccountId().toSolidityAddress(), 10L, false);
        var args = Tuple.of((Object) new Tuple[]{senderTransfer, receiverTransfer});
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.CRYPTO_TRANSFER_HBARS.getFunctionName())
                .encodeCallWithArgs(args, EMPTY_TUPLE_ARRAY);
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CRYPTO_TRANSFER_HBARS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
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
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.CRYPTO_TRANSFER_NFT.getFunctionName())
                .encodeCallWithArgs(Tuple.of((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CRYPTO_TRANSFER_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with cryptoTransfer function for fungible tokens")
    public void cryptoTransferFungibleEstimateGas() throws InvalidProtocolBufferException {
        networkTransactionResponse = tokenClient.grantKyc(tokenIds.get(0), receiverAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var tokenTransferList = (Object) new Tuple[]{tokenTransferList()
                .forToken(tokenIds.get(0).toSolidityAddress())
                .withAccountAmounts(
                        accountAmount(admin.getAccountId().toSolidityAddress(), -3L, false),
                        accountAmount(secondReceiverAccount.getAccountId().toSolidityAddress(), 3L, false))
                .build()};
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.CRYPTO_TRANSFER.getFunctionName())
                .encodeCallWithArgs(Tuple.of((Object) EMPTY_TUPLE_ARRAY), tokenTransferList);
        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CRYPTO_TRANSFER.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with mintToken function for fungible token")
    public void mintFungibleTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.MINT_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        1L,
                        asHeadlongByteArray(new ArrayList<>()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.MINT_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with mintToken function for NFT")
    public void mintNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.MINT_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        0L,
                        asHeadlongByteArray(Arrays.asList("0x02")));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.MINT_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with burnToken function for fungible token")
    public void burnFungibleTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.BURN_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        1L,
                        asLongArray(new ArrayList<>()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.BURN_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with burnToken function for NFT")
    public void burnNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.BURN_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        0L,
                        asLongArray(List.of(1L)));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.BURN_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with CreateFungibleToken function")
    public void createFungibleTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.CREATE_FUNGIBLE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CREATE_FUNGIBLE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with CreateNFT function")
    public void createNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.CREATE_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.CREATE_NFT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with CreateFungibleToken function with custom fees")
    public void createFungibleTokenWithCustomFeesEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimationForTokenCreateWithCustomFees(Strings.encode(encodedFunctionCall),
                ContractMethods.CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES.getActualGas());
    }

    @Then("I call estimateGas with CreateNFT function with custom fees")
    public void createNFTWithCustomFeesEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimationForTokenCreateWithCustomFees(Strings.encode(encodedFunctionCall),
                ContractMethods.CREATE_NFT_WITH_CUSTOM_FEES.getActualGas());
    }

    @Then("I call estimateGas with WipeTokenAccount function")
    public void wipeTokenAccountEstimateGas() {
        tokenClient.grantKyc(tokenIds.get(0), receiverAccount.getAccountId());
        accountClient.approveToken(tokenIds.get(0), receiverAccount.getAccountId(), 10_000_000_000L);
        networkTransactionResponse = tokenClient.transferFungibleToken(tokenIds.get(0), admin,
                receiverAccount.getAccountId(), 3_000_000_000_0L);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.WIPE_TOKEN_ACCOUNT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        1L);

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.WIPE_TOKEN_ACCOUNT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with WipeTokenAccount function with invalid amount")
    public void wipeTokenAccountInvalidAmountEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.WIPE_TOKEN_ACCOUNT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        100000000000000000L);

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with WipeNFTAccount function")
    public void wipeNFTAccountEstimateGas() {
        tokenClient.grantKyc(tokenIds.get(1), receiverAccount.getAccountId());
        NftId id = new NftId(tokenIds.get(1), firstNftSerialNumber);
        accountClient.approveNft(id, receiverAccount.getAccountId());
        networkTransactionResponse = tokenClient.transferNonFungibleToken(tokenIds.get(1), admin,
                receiverAccount.getAccountId(), Collections.singletonList(firstNftSerialNumber));
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.WIPE_NFT_ACCOUNT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asLongArray(List.of(1L)));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.WIPE_NFT_ACCOUNT.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with WipeNFTAccount function with invalid serial number")
    public void wipeNFTAccountInvalidSerialNumberEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.WIPE_NFT_ACCOUNT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()),
                        asLongArray(List.of(66L)));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with GrantKYC function for fungible token")
    public void grantKYCFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GRANT_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GRANT_KYC.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with GrantKYC function for NFT")
    public void grantKYCNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GRANT_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GRANT_KYC.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I create fungible and non-fungible token without KYC status")
    public void createFungibleTokenWithoutKYC() {
        createTokenWithCustomFees(TokenKycStatus.KycNotApplicable_VALUE);
        createNonFungibleTokenWithFixedFee(TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Then("I call estimateGas with GrantKYC function for fungible token without KYC status")
    public void grantKYCFungibleNegativeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GRANT_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(6).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with GrantKYC function for NFT without KYC status")
    public void grantKYCNonFungibleNegativeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.GRANT_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(7).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }


    @Then("I call estimateGas with RevokeTokenKYC function for fungible token")
    public void revokeTokenKYCEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.REVOKE_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.REVOKE_KYC.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with RevokeTokenKYC function for NFT")
    public void revokeTokenKYCNonFungibleEstimateGas() {
//        tokenClient.grantKyc(tokenIds.get(1), receiverAccount.getAccountId());
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.REVOKE_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.REVOKE_KYC.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with RevokeTokenKYC function on a token without KYC")
    public void revokeTokenKYCNegativeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.REVOKE_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(6).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Grant and Revoke KYC nested function")
    public void nestedGrantRevokeKYCEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.NESTED_GRANT_REVOKE_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(2).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.NESTED_GRANT_REVOKE_KYC.getActualGas(), estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Freeze function for fungible token")
    public void freezeFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.FREEZE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.FREEZE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Freeze function for NFT")
    public void freezeNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.FREEZE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.FREEZE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Unfreeze function for fungible token")
    public void unfreezeFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.UNFREEZE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UNFREEZE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with Unfreeze function for NFT")
    public void unfreezeNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.UNFREEZE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UNFREEZE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for fungible token")
    public void nestedFreezeAndUnfreezeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.NESTED_FREEZE_UNFREEZE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.NESTED_FREEZE_UNFREEZE.getActualGas(), estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with nested Freeze and Unfreeze function for NFT")
    public void nestedFreezeAndUnfreezeNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.NESTED_FREEZE_UNFREEZE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.NESTED_FREEZE_UNFREEZE.getActualGas(), estimatePrecompileContractSolidityAddress);
    }


    @Then("I call estimateGas with delete function for Fungible token")
    public void deleteFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DELETE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DELETE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with delete function for NFT")
    public void deleteNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.DELETE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DELETE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with delete function for invalid token address")
    public void deleteTokenRandomAddressEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.DELETE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(RANDOM_ADDRESS));

        assertContractCallReturnsBadRequest(encodedFunctionCall, estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with pause function for fungible token")
    public void pauseFungibleTokenPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.PAUSE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.PAUSE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with pause function for NFT")
    public void pauseNFTPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(ContractMethods.PAUSE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.PAUSE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with unpause function for fungible token")
    public void unpauseFungibleTokenPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.UNPAUSE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UNPAUSE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with unpause function for NFT")
    public void unpauseNFTPositiveEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.UNPAUSE_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UNPAUSE_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas for nested pause and unpause function")
    public void pauseUnpauseFungibleTokenNestedCallEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getActualGas(), estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas for nested pause, unpause NFT function")
    public void pauseUnpauseNFTNestedCallEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.PAUSE_UNPAUSE_NESTED_TOKEN.getActualGas(), estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with updateTokenExpiryInfo function")
    public void updateTokenExpiryInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.UPDATE_TOKEN_EXPIRY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UPDATE_TOKEN_EXPIRY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with updateTokenInfo function")
    public void updateTokenInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.UPDATE_TOKEN_INFO.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UPDATE_TOKEN_INFO.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with updateTokenKeys function")
    public void updateTokenKeysEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.UPDATE_TOKEN_KEYS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.UPDATE_TOKEN_KEYS.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenExpiryInfo function")
    public void getTokenExpiryInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.GET_TOKEN_EXPIRY_INFO.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_TOKEN_EXPIRY_INFO.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with isToken function")
    public void isTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.IS_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.IS_TOKEN.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for supply")
    public void getTokenKeySupplyEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.GET_TOKEN_KEY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        new BigInteger("16"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for KYC")
    public void getTokenKeyKYCEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.GET_TOKEN_KEY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        new BigInteger("2"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for freeze")
    public void getTokenKeyFreezeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.GET_TOKEN_KEY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        new BigInteger("4"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for admin")
    public void getTokenKeyAdminEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.GET_TOKEN_KEY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        new BigInteger("1"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for wipe")
    public void getTokenKeyWipeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.GET_TOKEN_KEY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        new BigInteger("8"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for fee")
    public void getTokenKeyFeeEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.GET_TOKEN_KEY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        new BigInteger("32"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenKey function for pause")
    public void getTokenKeyPauseEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.GET_TOKEN_KEY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        new BigInteger("64"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_TOKEN_KEY.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with allowance function for fungible token")
    public void allowanceFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.ALLOWANCE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ALLOWANCE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with allowance function for NFT")
    public void allowanceNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.ALLOWANCE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ALLOWANCE.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC allowance function for fungible token")
    public void ercAllowanceFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.ALLOWANCE_ERC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.ALLOWANCE_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getApproved function for NFT")
    public void getApprovedNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.GET_APPROVED.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        new BigInteger("1"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_APPROVED.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC getApproved function for NFT")
    public void ercGetApprovedNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.GET_APPROVED_ERC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        new BigInteger("1"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.GET_APPROVED_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isApprovedForAll function")
    public void isApprovedForAllEstimateGas() {
        //reminder: check with setApprovalForAll test-> there we have the contract associated so the test can work
        //so we might need to change the admin to contractID
        ByteBuffer encodedFunctionCall = getFunctionFromEstimateArtifact(
                ContractMethods.IS_APPROVED_FOR_ALL.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.IS_APPROVED_FOR_ALL.getActualGas(),
                estimatePrecompileContractSolidityAddress);
    }

    @Then("I call estimateGas with ERC isApprovedForAll function")
    public void ercIsApprovedForAllEstimateGas() {
        //reminder: check with setApprovalForAll test-> there we have the contract associated so the test can work
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.IS_APPROVED_FOR_ALL_ERC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.IS_APPROVED_FOR_ALL_ERC.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with name function for fungible token")
    public void nameEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.NAME.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.NAME.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with name function for NFT")
    public void nameNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.NAME_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.NAME_NFT.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with symbol function for fungible token")
    public void symbolEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.SYMBOL.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.SYMBOL.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with symbol function for NFT")
    public void symbolNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.SYMBOL_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.SYMBOL_NFT.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with decimals function for fungible token")
    public void decimalsEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.DECIMALS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.DECIMALS.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with totalSupply function for fungible token")
    public void totalSupplyEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.TOTAL_SUPPLY.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TOTAL_SUPPLY.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with totalSupply function for NFT")
    public void totalSupplyNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.TOTAL_SUPPLY_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TOTAL_SUPPLY_NFT.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with balanceOf function for fungible token")
    public void balanceOfEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.BALANCE_OF.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.BALANCE_OF.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with balanceOf function for NFT")
    public void balanceOfNFTEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.BALANCE_OF_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(admin.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.BALANCE_OF_NFT.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with ownerOf function for NFT")
    public void ownerOfEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.OWNER_OF.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        new BigInteger("1"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.OWNER_OF.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with tokenURI function for NFT")
    public void tokenURIEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromErcArtifact(
                ContractMethods.TOKEN_URI.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        new BigInteger("1"));

        validateGasEstimation(Strings.encode(encodedFunctionCall), ContractMethods.TOKEN_URI.getActualGas(),
                ercTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getFungibleTokenInfo function")
    public void getFungibleTokenInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_FUNGIBLE_TOKEN_INFO.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_FUNGIBLE_TOKEN_INFO.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getNonFungibleTokenInfo function")
    public void getNonFungibleTokenInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_NON_FUNGIBLE_TOKEN_INFO.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        1L);

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_NON_FUNGIBLE_TOKEN_INFO.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenInfo function for fungible")
    public void getTokenInfoEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_TOKEN_INFO.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_INFO.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenInfo function for NFT")
    public void getTokenInfoNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_TOKEN_INFO_NFT.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_INFO_NFT.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for fungible token")
    public void getTokenDefaultFreezeStatusFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultFreezeStatus function for NFT")
    public void getTokenDefaultFreezeStatusNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_DEFAULT_FREEZE_STATUS.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for fungible token")
    public void getTokenDefaultKycStatusFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenDefaultKycStatus function for NFT")
    public void getTokenDefaultKycStatusNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_DEFAULT_KYC_STATUS.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isKyc function for fungible token")
    public void isKycFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.IS_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.IS_KYC.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isKyc function for NFT")
    public void isKycNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.IS_KYC.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.IS_KYC.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isFrozen function for fungible token")
    public void isFrozenFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.IS_FROZEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.IS_FROZEN.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with isFrozen function for NFT")
    public void isFrozenNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.IS_FROZEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()),
                        asHeadlongAddress(receiverAccount.getAccountId().toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.IS_FROZEN.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenType function for fungible token")
    public void getTokenTypeFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_TOKEN_TYPE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_TYPE.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with getTokenType function for NFT")
    public void getTokenTypeNonFungibleEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.GET_TOKEN_TYPE.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(1).toSolidityAddress()));

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.GET_TOKEN_TYPE.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    @Then("I call estimateGas with redirectForToken function")
    public void redirectForTokenEstimateGas() {
        ByteBuffer encodedFunctionCall = getFunctionFromPrecompileArtifact(
                ContractMethods.REDIRECT_FOR_TOKEN.getFunctionName())
                .encodeCallWithArgs(
                        asHeadlongAddress(tokenIds.get(0).toSolidityAddress()),
                        new byte[]{(byte) 0x06, (byte) 0xfd, (byte) 0xde, (byte) 0x03});

        validateGasEstimation(Strings.encode(encodedFunctionCall),
                ContractMethods.REDIRECT_FOR_TOKEN.getActualGas(),
                precompileTestContractSolidityAddress);
    }

    public void assertContractCallReturnsBadRequest(ByteBuffer encodedFunctionCall, String contractAddress) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .to(contractAddress)
                .estimate(true)
                .build();

        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    private void validateGasEstimationForTokenCreateWithCustomFees(String selector, int actualGasUsed) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(selector)
                .to(estimatePrecompileContractSolidityAddress)
                .estimate(true)
                .gas(30_000_000)
                .build();
        ContractCallResponse msgSenderResponse = mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();
        assertTrue(isWithinDeviation(actualGasUsed, estimatedGas, lowerDeviation, upperDeviation));
    }

    public String getAbiFunctionAsJsonString(CompiledSolidityArtifact compiledSolidityArtifact, String functionName) {
        Optional<Object> function = Arrays.stream(compiledSolidityArtifact.getAbi())
                .filter(item -> {
                    Object name = ((LinkedHashMap) item).get("name");
                    return name != null && name.equals(functionName);
                })
                .findFirst();

        if (function.isPresent()) {
            return (new JSONObject((Map) function.get())).toString();
        } else {
            throw new IllegalStateException("Function " + functionName + " is not present in the ABI.");
        }
    }

    public Function getFunctionFromEstimateArtifact(String functionName) {
        String json = getAbiFunctionAsJsonString(compiledEstimatePrecompileSolidityArtifacts, functionName);
        return Function.fromJson(json);
    }

    public Function getFunctionFromErcArtifact(String functionName) {
        String json = getAbiFunctionAsJsonString(compiledErcTestContractSolidityArtifacts, functionName);
        return Function.fromJson(json);
    }

    public Function getFunctionFromPrecompileArtifact(String functionName) {
        String json = getAbiFunctionAsJsonString(compiledPrecompileContractSolidityArtifacts, functionName);
        return Function.fromJson(json);
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

    private void validateGasEstimation(String selector, int actualGasUsed, String solidityAddress) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(selector)
                .to(solidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse msgSenderResponse =
                mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();

        assertTrue(isWithinDeviation(actualGasUsed, estimatedGas, lowerDeviation, upperDeviation));
    }

    private void createNewToken(
            String symbol, TokenType tokenType, int kycStatus, TokenSupplyType tokenSupplyType,
            List<CustomFee> customFees) {
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
         * 0-expecting a revert 23422-gas estimation is not calculated
         */
        ALLOWANCE("allowanceExternal", 25399),
        ALLOWANCE_ERC("allowance", 27481),
        APPROVE("approveExternal", 729571),
        APPROVE_NFT("approveNFTExternal", 729569),
        APPROVE_ERC("approve", 731632),
        ASSOCIATE_TOKEN("associateTokenExternal", 729374),
        ASSOCIATE_TOKENS("associateTokensExternal", 730641),
        BALANCE_OF("balanceOf", 27270),
        BALANCE_OF_NFT("balanceOfIERC721", 27228),
        BURN_TOKEN("burnTokenExternal", 40247),
        CREATE_FUNGIBLE_TOKEN("createFungibleTokenPublic", 11590398),
        CREATE_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES("createFungibleTokenWithCustomFeesPublic", 23134640),
        CREATE_NFT("createNonFungibleTokenPublic", 11574020),
        CREATE_NFT_WITH_CUSTOM_FEES("createNonFungibleTokenWithCustomFeesPublic", 23133124),
        CRYPTO_TRANSFER("cryptoTransferExternal", 44438),
        CRYPTO_TRANSFER_HBARS("cryptoTransferExternal", 29698),
        CRYPTO_TRANSFER_NFT("cryptoTransferExternal", 57934),
        DECIMALS("decimals", 27143),
        DELETE_TOKEN("deleteTokenExternal", 39095),
        DISSOCIATE_AND_ASSOCIATE("dissociateAndAssociateTokenExternal", 1434814),
        DISSOCIATE_TOKEN("dissociateTokenExternal", 729428),
        DISSOCIATE_TOKENS("dissociateTokensExternal", 730641),
        FREEZE_TOKEN("freezeTokenTwiceExternal", 54526),
        GET_APPROVED("getApprovedExternal", 25192),
        GET_APPROVED_ERC("getApproved", 27393),
        GET_FUNGIBLE_TOKEN_INFO("getInformationForFungibleToken", 58190),
        GET_NON_FUNGIBLE_TOKEN_INFO("getInformationForNonFungibleToken", 59159),
        GET_TOKEN_DEFAULT_FREEZE_STATUS("getTokenDefaultFreeze", 25191),
        GET_TOKEN_DEFAULT_KYC_STATUS("getTokenDefaultKyc", 25267),
        GET_TOKEN_EXPIRY_INFO("getTokenExpiryInfoExternal", 25617),
        GET_TOKEN_INFO("getInformationForToken", 57612),
        GET_TOKEN_INFO_NFT("getInformationForToken", 56523),
        GET_TOKEN_KEY("getTokenKeyExternal", 27024),
        GET_TOKEN_TYPE("getType", 25223),
        GRANT_KYC("grantTokenKycExternal", 39311),
        IS_APPROVED_FOR_ALL("isApprovedForAllExternal", 25483),
        IS_APPROVED_FOR_ALL_ERC("isApprovedForAll", 27520),
        IS_TOKEN("isTokenExternal", 25100),
        IS_FROZEN("isTokenFrozen", 25473),
        IS_KYC("isKycGranted", 25417),
        MINT_TOKEN("mintTokenExternal", 40700),
        MINT_NFT("mintTokenExternal", 732250),
        NAME("name", 27976),
        NAME_NFT("nameIERC721", 27837),
        NESTED_ASSOCIATE("nestedAssociateTokenExternal", 0),
        NESTED_FREEZE_UNFREEZE("nestedFreezeUnfreezeTokenExternal", 54548),
        NESTED_GRANT_REVOKE_KYC("nestedGrantAndRevokeTokenKYCExternal", 54516),
        OWNER_OF("getOwnerOf", 27271),
        REVOKE_KYC("revokeTokenKycExternal", 39324),
        REDIRECT_FOR_TOKEN("redirectForTokenExternal", 23422),
        SET_APPROVAL_FOR_ALL("setApprovalForAllExternal", 729608),
        SYMBOL("symbol", 27815),
        SYMBOL_NFT("symbolIERC721", 27814),
        TOTAL_SUPPLY("totalSupply", 27100),
        TOTAL_SUPPLY_NFT("totalSupplyIERC721", 27078),
        TOKEN_URI("tokenURI", 27856),
        TRANSFER_ERC("transfer", 41414),
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
        PAUSE_UNPAUSE_NESTED_TOKEN("nestedPauseUnpauseTokenExternal", 54237),
        UNPAUSE_TOKEN("unpauseTokenExternal", 39112),
        UPDATE_TOKEN_EXPIRY("updateTokenExpiryInfoExternal", 39699),
        UPDATE_TOKEN_INFO("updateTokenInfoExternal", 74920),
        UPDATE_TOKEN_KEYS("updateTokenKeysExternal", 60427);

        private final String functionName;
        private final int actualGas;
    }

    private record DeployedContract(FileId fileId, ContractId contractId,
                                    CompiledSolidityArtifact compiledSolidityArtifact) {
    }
}
