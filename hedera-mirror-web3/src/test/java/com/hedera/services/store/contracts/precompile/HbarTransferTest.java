package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.a;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.b;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.secondAmount;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HbarTransferTest {

    @Test
    void createsExpectedCryptoTransfer() {
        final var hbarTransfer = new HbarTransfer(secondAmount, false, b, a);
        assertFalse(hbarTransfer.isApproval());
        assertEquals(b, hbarTransfer.sender());
        assertEquals(a, hbarTransfer.receiver());
        assertEquals(200, hbarTransfer.amount());
        assertEquals(200, hbarTransfer.receiverAdjustment().getAmount());
        assertEquals(-200, hbarTransfer.senderAdjustment().getAmount());
    }
}
