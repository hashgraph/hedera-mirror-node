package com.hedera.mirror.grpc.jmeter.sampler.result;

import com.google.common.base.Stopwatch;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.TransactionId;

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

@Data
@Log4j2
public class TransactionSubmissionResult {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final Stopwatch totalStopwatch = Stopwatch.createStarted();
    private final List<TransactionId> transactionIdList = new ArrayList<>();

    public void onNext(TransactionId transactionId) {
        transactionIdList.add(transactionId);
        counter.incrementAndGet();
        log.trace("Published a message w transactionId: {}", transactionId);
    }

    public void onComplete() {
        totalStopwatch.stop();

        printProgress();
    }

    public static double getTransactionSubmissionRate(long transactionCount, long milliSeconds) {
        if (transactionCount == 0) {
            return 0;
        }

        if (milliSeconds < 1000) {
            return transactionCount;
        }

        return milliSeconds > 0 ? (transactionCount * 1.0 / milliSeconds) * 1000.0 : 0;
    }

    public void printProgress() {
        log.debug("Published {} transactions in {} s ({}/s)", counter.get(), totalStopwatch
                .elapsed(TimeUnit.SECONDS), getTransactionSubmissionRate(counter.get(), totalStopwatch
                .elapsed(TimeUnit.MILLISECONDS)));
    }
}
