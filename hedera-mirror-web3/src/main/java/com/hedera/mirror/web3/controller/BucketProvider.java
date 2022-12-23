package com.hedera.mirror.web3.controller;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;

@Named
@RequiredArgsConstructor
public class BucketProvider {

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public Bucket getBucket() {
        final var rateLimitPerSecond = mirrorNodeEvmProperties.getRateLimitPerSecond();
        final var limit = Bandwidth.classic(rateLimitPerSecond, Refill.greedy(rateLimitPerSecond, Duration.ofSeconds(1)));

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
