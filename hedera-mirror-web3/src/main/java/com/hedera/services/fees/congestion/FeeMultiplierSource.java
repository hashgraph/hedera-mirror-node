package com.hedera.services.fees.congestion;

import com.hedera.services.utils.accessors.TxnAccessor;

import java.time.Instant;

public interface FeeMultiplierSource {
    void updateMultiplier(final TxnAccessor accessor, Instant consensusNow);

    long currentMultiplier(final TxnAccessor accessor);

    void resetExpectations();

    void resetCongestionLevelStarts(Instant[] savedStartTimes);

    Instant[] congestionLevelStarts();

}
