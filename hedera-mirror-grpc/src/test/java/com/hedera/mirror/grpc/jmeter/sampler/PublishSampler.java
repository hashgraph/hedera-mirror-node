package com.hedera.mirror.grpc.jmeter.sampler;

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
