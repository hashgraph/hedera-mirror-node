/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.client;

import com.hedera.hashgraph.sdk.FileAppendTransaction;
import com.hedera.hashgraph.sdk.FileCreateTransaction;
import com.hedera.hashgraph.sdk.FileDeleteTransaction;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.FileInfo;
import com.hedera.hashgraph.sdk.FileInfoQuery;
import com.hedera.hashgraph.sdk.FileUpdateTransaction;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import jakarta.inject.Named;
import org.springframework.retry.support.RetryTemplate;

@Named
public class FileClient extends AbstractNetworkClient {

    public FileClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    public NetworkTransactionResponse createFile(byte[] content) {
        var memo = getMemo("Create file");
        FileCreateTransaction fileCreateTransaction = new FileCreateTransaction()
                .setKeys(sdkClient.getExpandedOperatorAccountId().getPublicKey())
                .setContents(content)
                .setFileMemo(memo)
                .setTransactionMemo(memo);

        var keyList = KeyList.of(sdkClient.getExpandedOperatorAccountId().getPrivateKey());
        var response = executeTransactionAndRetrieveReceipt(fileCreateTransaction, keyList);

        var fileId = response.getReceipt().fileId;
        log.info("Created new file {} with {} B via {}", fileId, content.length, memo, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse updateFile(FileId fileId, byte[] contents) {
        var memo = getMemo("Update file");
        FileUpdateTransaction fileUpdateTransaction =
                new FileUpdateTransaction().setFileId(fileId).setFileMemo(memo).setTransactionMemo(memo);

        int count = 0;
        if (contents != null) {
            fileUpdateTransaction.setContents(contents);
            count = contents.length;
        }

        var response = executeTransactionAndRetrieveReceipt(fileUpdateTransaction);
        log.info("Updated file {} with {} B via {}", fileId, count, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse appendFile(FileId fileId, byte[] contents) {
        var memo = getMemo("Append file");
        FileAppendTransaction fileAppendTransaction = new FileAppendTransaction()
                .setFileId(fileId)
                .setContents(contents)
                .setTransactionMemo(memo);

        var response = executeTransactionAndRetrieveReceipt(fileAppendTransaction);
        log.info("Appended {} B to file {} via {}", contents.length, fileId, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse deleteFile(FileId fileId) {
        var memo = getMemo("Delete file");
        FileDeleteTransaction fileUpdateTransaction =
                new FileDeleteTransaction().setFileId(fileId).setTransactionMemo(memo);

        var response = executeTransactionAndRetrieveReceipt(fileUpdateTransaction);
        log.info("Deleted file {} with memo '{}' via {}", fileId, memo, response.getTransactionId());
        return response;
    }

    public FileInfo getFileInfo(FileId fileId) {
        return executeQuery(() -> new FileInfoQuery().setFileId(fileId));
    }
}
