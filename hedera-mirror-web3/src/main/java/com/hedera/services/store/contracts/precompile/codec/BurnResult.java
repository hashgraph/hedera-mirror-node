package com.hedera.services.store.contracts.precompile.codec;

import java.util.List;

public record BurnResult(long totalSupply, List<Long> serialNumbers) implements RunResult {}