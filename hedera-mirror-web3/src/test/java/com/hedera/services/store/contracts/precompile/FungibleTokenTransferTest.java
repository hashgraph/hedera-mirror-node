package com.hedera.services.store.contracts.precompile;

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FungibleTokenTransferTest {

     static final long secondAmount = 200;
     static final AccountID a = asAccount("0.0.2");
     static final AccountID b = asAccount("0.0.3");
     static final TokenID fungible = asToken("0.0.555");
    static final TokenID nonFungible = asToken("0.0.666");

    @Test
    void createsExpectedCryptoTransfer() {
        final var fungibleTransfer = new FungibleTokenTransfer(secondAmount, false, fungible, b, a);
        assertTrue(fungibleTransfer.getDenomination().equals(fungible));
    }

    static AccountID asAccount(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return AccountID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setAccountNum(nativeParts[2])
                .build();
    }

    static TokenID asToken(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return TokenID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setTokenNum(nativeParts[2])
                .build();
    }


    //copied from IdUtils
    static long[] asDotDelimitedLongArray(String s) {
        String[] parts = s.split("[.]");
        return Stream.of(parts).mapToLong(Long::valueOf).toArray();
    }
}
