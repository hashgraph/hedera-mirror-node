package com.hedera.services.transaction;

import com.hederahashgraph.api.proto.java.TokenID;

public record RedirectTarget(int descriptor, TokenID tokenId, byte[] address) {}
