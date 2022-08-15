package com.hedera.services.transaction;

public record RedirectTarget(int descriptor, byte[] address) {}
