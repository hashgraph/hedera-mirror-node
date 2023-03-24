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
        assertTrue(hbarTransfer.sender().equals(b));
        assertTrue(hbarTransfer.receiver().equals(a));
        assertTrue(hbarTransfer.amount() == 200);
        assertTrue(hbarTransfer.receiverAdjustment().getAmount() == 200);
        assertTrue(hbarTransfer.senderAdjustment().getAmount() == -200);
    }
}
