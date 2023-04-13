package com.hedera.services.hapi.fees.usage.schedule.entities;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.KEY_SIZE;

import com.hedera.services.hapi.fees.usage.SigUsage;

public enum ScheduleEntitySizes {
    SCHEDULE_ENTITY_SIZES;

    public int bytesUsedForSigningKeys(int n) {
        return n * KEY_SIZE;
    }

    public int estimatedScheduleSigs(SigUsage sigUsage) {
        return Math.max(sigUsage.numSigs() - sigUsage.numPayerKeys(), 1);
    }
}
