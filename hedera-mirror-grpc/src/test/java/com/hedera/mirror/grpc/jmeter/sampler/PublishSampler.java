package com.hedera.mirror.grpc.jmeter.sampler;

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

import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

@Log4j2
public class PublishSampler {
    protected final SummaryStatistics publishLatencyStatistics = new SummaryStatistics();

    protected void printPublishStats(String customMessage) {
        // Compute some statistics
        double min = publishLatencyStatistics.getMin();
        double max = publishLatencyStatistics.getMax();
        double mean = publishLatencyStatistics.getMean();

        log.info("{}: min: {} ms, max: {} ms, avg: {} ms", customMessage, String.format("%.03f", min), String
                .format("%.03f", max), String.format("%.03f", mean));
    }
}
