package com.hedera.mirror.test.e2e.acceptance.steps;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.junit.platform.engine.Cucumber;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.FileInfo;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Cucumber
public class FileFeature {
    private final static String originalFileContents = "Mirror Node v1";
    private final static String updateBaseFileContents = "Mirror Node v2,";
    private final static String appendFileContents = " new and improved";
    private final static String updatedFileContents = updateBaseFileContents + appendFileContents;
    @Autowired
    private FileClient fileClient;
    @Autowired
    private MirrorNodeClient mirrorClient;

    private NetworkTransactionResponse networkTransactionResponse;
    private FileId fileId;
    private FileInfo fileInfo;

    @Given("I successfully create a file")
    public void createNewFile() {
        networkTransactionResponse = fileClient.createFile(originalFileContents.getBytes(StandardCharsets.UTF_8));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);
    }

    @Given("I successfully update the file with {string} bytes")
    public void updateFile(String bytesLength) {
        String updateContents = "";
        switch (bytesLength) {
            case "FULL":
                updateContents = updatedFileContents;
                break;
            case "PARTIAL":
                updateContents = updateBaseFileContents;
                break;
            default:
                break;
        }

        networkTransactionResponse = fileClient.updateFile(fileId, updateContents.getBytes(StandardCharsets.UTF_8));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully append to the file")
    public void appendFile() {
        networkTransactionResponse = fileClient.appendFile(fileId, appendFileContents.getBytes(StandardCharsets.UTF_8));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully delete the file")
    public void deleteFile() {
        networkTransactionResponse = fileClient.deleteFile(fileId);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the network confirms file presence")
    public void verifyNetworkFileCreateResponse() {
        fileInfo = fileClient.getFileInfo(fileId);

        verifyFileInfo(fileInfo, originalFileContents);
    }

    @Then("the network confirms file update")
    public void verifyNetworkFileUpdateResponse() {
        verifyNetworkUpdateResponse(false);
    }

    @Then("the network confirms partial file contents")
    public void verifyNoNetworkFileUpdateResponse() {
        var newFileInfo = fileClient.getFileInfo(fileId);

        verifyFileInfo(newFileInfo, updateBaseFileContents);

        assertThat(fileInfo.fileMemo).isNotEqualTo(newFileInfo.fileMemo);
        assertThat(fileInfo.size).isNotEqualTo(newFileInfo.size);

        fileInfo = newFileInfo;
    }

    @Then("the network confirms an append update")
    public void verifyNetworkFileAppendResponse() {
        verifyNetworkUpdateResponse(true);
    }

    private void verifyNetworkUpdateResponse(boolean append) {
        var newFileInfo = fileClient.getFileInfo(fileId);

        verifyFileInfo(newFileInfo, updatedFileContents);

        if (append) {
            assertThat(fileInfo.fileMemo).isEqualTo(newFileInfo.fileMemo);
        } else {
            assertThat(fileInfo.fileMemo).isNotEqualTo(newFileInfo.fileMemo);
        }

        assertThat(fileInfo.size).isNotEqualTo(newFileInfo.size);

        fileInfo = newFileInfo;
    }

    private void verifyFileInfo(FileInfo newFileInfo, String contents) {
        assertThat(newFileInfo.fileMemo).isNotEmpty();
        assertThat(newFileInfo.expirationTime).isNotNull();
        assertThat(newFileInfo.size).isEqualTo(contents.getBytes(StandardCharsets.UTF_8).length);
        assertThat(newFileInfo.keys).isNotEmpty();
    }

    @Then("the network confirms file absence")
    public void verifyNetworkFileDeleteResponse() {
        var fileInfo = fileClient.getFileInfo(fileId);

        assertThat(fileInfo.isDeleted).isTrue();
    }

    @Then("the mirror node REST API should return status {int} for the file transaction")
    public void verifyMirrorAPIResponses(int status) {
        log.info("Verify file transaction");
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorTransactionsResponse, status);

        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
    }

    private MirrorTransaction verifyMirrorTransactionsResponse(MirrorTransactionsResponse mirrorTransactionsResponse,
                                                               int status) {
        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isNotNull();
        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getEntityId()).isEqualTo(fileId.toString());

        return mirrorTransaction;
    }
}
