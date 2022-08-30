package com.hedera.services.transaction;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public record MintWrapper(long amount, Address tokenAddress, List<ByteString> metadata) {
    private static final long NONFUNGIBLE_MINT_AMOUNT = -1;
    private static final List<ByteString> FUNGIBLE_MINT_METADATA = Collections.emptyList();

    public static MintWrapper forNonFungible(
            final Address tokenAddress, final List<ByteString> metadata) {
        return new MintWrapper(NONFUNGIBLE_MINT_AMOUNT, tokenAddress, metadata);
    }

    public static MintWrapper forFungible(final Address tokenAddress, final long amount) {
        return new MintWrapper(amount, tokenAddress, FUNGIBLE_MINT_METADATA);
    }

    public TokenType type() {
        return (amount == NONFUNGIBLE_MINT_AMOUNT) ? NON_FUNGIBLE_UNIQUE : FUNGIBLE_COMMON;
    }
}
