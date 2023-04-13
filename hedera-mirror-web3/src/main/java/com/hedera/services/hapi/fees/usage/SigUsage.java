package com.hedera.services.hapi.fees.usage;

public record SigUsage(int numSigs, int sigsSize, int numPayerKeys) {}
