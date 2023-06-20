package com.hedera.mirror.test.e2e.acceptance.steps;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.*;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.*;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorNftResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EstimatePrecompileFeature extends AbstractFeature {
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final String HEX_DIGITS = "0123456789abcdef";
    private static final String RANDOM_ADDRESS = to32BytesString(RandomStringUtils.random(40, HEX_DIGITS));
    private static final long firstNftSerialNumber = 1;
    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;
    private final List<TokenId> tokenIds = new ArrayList<>();
    private final AccountClient accountClient;
    private DeployedContract deployedContract;
    private ExpandedAccountId receiverAccount;
    private ExpandedAccountId spenderAccount;
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
        spenderAccount = accountClient.createNewECDSAAccount(10_000_000);
    }

    @Given("I successfully create and verify a fungible token for estimateGas precompile tests")
    public void createFungibleToken() {
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

    @Given("I successfully create and verify a non fungible token for estimateGas precompile tests")
    public void createNonFungibleToken() {
        CustomFixedFee customFixedFee = new CustomFixedFee();
        customFixedFee.setAmount(10);
        customFixedFee.setFeeCollectorAccountId(admin.getAccountId());

        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenSupplyType.INFINITE,
                List.of(customFixedFee));
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

    @Then("I call estimateGas with associate token function")
    public void associateFunctionEstimateGas() {
        validateGasEstimation(
                ContractMethods.ASSOCIATE_TOKEN.getSelector()
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()), ContractMethods.ASSOCIATE_TOKEN.getActualGas());
        var test = "test";
    }

    @Then("I call estimateGas with dissociate token function without association")
    public void dissociateFunctionEstimateGasNegative() {
        //attempt to call dissociate function without having association
        //expecting status 400/revert
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.DISSOCIATE_TOKEN.getSelector()
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
//        validateGasEstimation(
//                ContractMethods.DISSOCIATE_TOKEN.getSelector()
//                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
//                        + to32BytesString(tokenIds.get(0).toSolidityAddress()), ContractMethods.DISSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with nested associate function that executes it twice")
    public void nestedAssociateFunctionEstimateGas() {
        //attempt to call associate function twice
        //expecting a revert
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.NESTED_ASSOCIATE.getSelector()
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
//        validateGasEstimation(
//                ContractMethods.NESTED_ASSOCIATE.getSelector()
//                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
//                        + to32BytesString(tokenIds.get(0).toSolidityAddress()), ContractMethods.NESTED_ASSOCIATE.getActualGas());
    }

    @Then("I call estimateGas with dissociate token function")
    public void dissociateFunctionEstimateGas() {
        //associating the token with the address
        tokenClient.associate(receiverAccount, tokenIds.get(0));

        validateGasEstimation(
                ContractMethods.DISSOCIATE_TOKEN.getSelector()
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()), ContractMethods.DISSOCIATE_TOKEN.getActualGas());
    }

    @Then("I call estimateGas with nested dissociate function that executes it twice")
    public void nestedDissociateFunctionEstimateGas() {
        //token is already associated
        //attempting to execute dissociate function twice
        //expecting a revert
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.NESTED_DISSOCIATE.getSelector()
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
//        validateGasEstimation(
//                ContractMethods.NESTED_DISSOCIATE.getSelector()
//                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
//                        + to32BytesString(tokenIds.get(0).toSolidityAddress()), ContractMethods.NESTED_DISSOCIATE.getActualGas());
    }

    @Then("I call estimateGas with dissociate and associate nested function")
    public void dissociateAndAssociatedEstimateGas() {
        //token is already associated
        //attempting to execute nested dissociate and associate function
        validateGasEstimation(
                ContractMethods.DISSOCIATE_AND_ASSOCIATE.getSelector()
                        + to32BytesString(receiverAccount.getAccountId().toSolidityAddress())
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()), ContractMethods.DISSOCIATE_AND_ASSOCIATE.getActualGas());
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
        tokenClient.associate(receiverAccount, tokenIds.get(1));
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
                TokenKycStatus.KycNotApplicable_VALUE,
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

    @Getter
    @RequiredArgsConstructor
    private enum ContractMethods {
        APPROVE("ceda64c4", 23422),
        APPROVE_NFT("84d232f5", 23422),
        ASSOCIATE_TOKEN("d91cfc95", 729374),
        ASSOCIATE_TOKENS("2fc358f7", 23422),
        DISSOCIATE_AND_ASSOCIATE("f1938266", 23422),
        DISSOCIATE_TOKEN("9c219247", 24030),
        DISSOCIATE_TOKENS("2390c1fa", 23422),
        NESTED_ASSOCIATE("437dffd5", 23422),
        NESTED_DISSOCIATE("f2d75676", 23422),
        TRANSFER_FROM("75a85472", 23422),
        TRANSFER_FROM_NFT("9bc4d354", 23422),
        TRANSFER_NFT("bafa6a91", 23422),
        TRANSFER_NFTS("1ba978fc", 23422),
        TRANSFER_TOKEN("4fd6ce0a", 23422),
        TRANSFER_TOKENS("aa835c63", 23422);

        private final String selector;
        private final int actualGas;
    }

    private record DeployedContract(FileId fileId, ContractId contractId,
                                    CompiledSolidityArtifact compiledSolidityArtifact) {
    }
}
