package com.hedera.services.hapi.fees.usage;

public record BaseTransactionMeta(int memoUtf8Bytes, int numExplicitTransfers) {}
