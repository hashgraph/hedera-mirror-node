package com.hedera.mirror.test.e2e.acceptance.client;

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

import javax.inject.Named;
import lombok.SneakyThrows;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.support.RetryTemplate;

import com.hedera.hashgraph.sdk.FileAppendTransaction;
import com.hedera.hashgraph.sdk.FileCreateTransaction;
import com.hedera.hashgraph.sdk.FileDeleteTransaction;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.FileInfo;
import com.hedera.hashgraph.sdk.FileInfoQuery;
import com.hedera.hashgraph.sdk.FileUpdateTransaction;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Named
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FileClient extends AbstractNetworkClient {
    public FileClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
        log.debug("Creating File Client");
    }

    public NetworkTransactionResponse createFile(byte[] content) {
        log.debug("Create new file");
        String memo = getMemo("Create file");
        FileCreateTransaction fileCreateTransaction = new FileCreateTransaction()
                .setKeys(sdkClient.getExpandedOperatorAccountId().getPublicKey())
                .setContents(content)
                .setFileMemo(memo)
                .setTransactionMemo(memo);

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                fileCreateTransaction,
                KeyList.of(sdkClient.getExpandedOperatorAccountId().getPrivateKey()));
        FileId fileId = networkTransactionResponse.getReceipt().fileId;
        log.debug("Created new file {}", fileId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateFile(FileId fileId, byte[] byteCode) {
        log.debug("Update file");
        String memo = getMemo("Update file");
        FileUpdateTransaction fileUpdateTransaction = new FileUpdateTransaction()
                .setFileId(fileId)
                .setFileMemo(memo)
                .setTransactionMemo(memo);

        if (byteCode != null) {
            fileUpdateTransaction.setContents(byteCode);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(fileUpdateTransaction);
        log.debug("Updated file {}", fileId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse appendFile(FileId fileId, byte[] byteCode) {
        String memo = "Append file";
        log.debug(memo);
        FileAppendTransaction fileAppendTransaction = new FileAppendTransaction()
                .setFileId(fileId)
                .setContents(byteCode)
                .setTransactionMemo(getMemo("Append file"));

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(fileAppendTransaction);
        log.debug("Appended to file {}", fileId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse deleteFile(FileId fileId) {
        String memo = "Delete file";
        log.debug(memo);
        FileDeleteTransaction fileUpdateTransaction = new FileDeleteTransaction()
                .setFileId(fileId)
                .setTransactionMemo(getMemo(memo));

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(fileUpdateTransaction);
        log.debug("Deleted file {}", fileId);

        return networkTransactionResponse;
    }

    @SneakyThrows
    public FileInfo getFileInfo(FileId fileId) {
        return retryTemplate.execute(x -> new FileInfoQuery()
                .setFileId(fileId)
                .execute(client));
    }
}
