package com.hedera.mirror.importer.downloader;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.base.Stopwatch;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * The results of a pending download from the AWS TransferManager. Call waitForCompletion() to wait for the transfer to
 * complete and get the status of whether it was successful or not.
 */
@Log4j2
@Value
class PendingDownload {
    private final Future<GetObjectResponse> future;
    private final Stopwatch stopwatch;
    private final File file; // Destination file
    private final String s3key; // Source S3 key

    @NonFinal
    private boolean alreadyWaited = false; // has waitForCompletion been called

    @NonFinal
    private boolean downloadSuccessful = false;

    PendingDownload(Future<GetObjectResponse> future, File file, String s3key) {
        this.future = future;
        stopwatch = Stopwatch.createStarted();
        this.file = file;
        this.s3key = s3key;
    }

    /**
     * @return true if the download was successful.
     */
    boolean waitForCompletion() throws InterruptedException {
        if (alreadyWaited) {
            return downloadSuccessful;
        }
        alreadyWaited = true;
        try {
            future.get();
            log.debug("Finished downloading {} in {}", s3key, stopwatch);
            downloadSuccessful = true;
        } catch (InterruptedException e) {
            log.warn("Failed downloading {} after {}", s3key, stopwatch, e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException ex) {
            log.warn("Failed downloading {} after {}: {}", s3key, stopwatch, ex.getMessage());
        } catch (Exception ex) {
            log.warn("Failed downloading {} after {}", s3key, stopwatch, ex);
        }
        return downloadSuccessful;
    }
}
