/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.FileInfo;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@CustomLog
@RequiredArgsConstructor
public class FileFeature {
    private static final String originalFileContents = "Mirror Node v1";
    private static final String updateBaseFileContents = "Mirror Node v2,";
    private static final String appendFileContents = " new and improved";
    private static final String updatedFileContents = updateBaseFileContents + appendFileContents;

    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;

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

    @Given("I successfully update the file")
    public void updateFile() {
        networkTransactionResponse =
                fileClient.updateFile(fileId, updateBaseFileContents.getBytes(StandardCharsets.UTF_8));

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

    @Then("the mirror node REST API should return status {int} for the file transaction")
    public void verifyMirrorAPIResponses(int status) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorTransactionsResponse, status);

        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
    }

    private MirrorTransaction verifyMirrorTransactionsResponse(
            MirrorTransactionsResponse mirrorTransactionsResponse, int status) {
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
