package com.hedera.services.store.contracts.precompile;

import org.junit.jupiter.api.Test;

import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.a;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.b;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.nonFungible;
import static org.junit.jupiter.api.Assertions.*;

class NftExchangeTest {

    @Test
    void createsExpectedCryptoTransfer() {
        final var nftExchange = new NftExchange(1L, nonFungible, a, b);
        assertFalse(nftExchange.isApproval());
        assertTrue(nftExchange.getTokenType().equals(nonFungible));
        assertTrue(nftExchange.asGrpc().hasSenderAccountID());
        assertTrue(nftExchange.getSerialNo() == 1L);
        assertTrue(NftExchange.fromApproval(1L, nonFungible, a, b).isApproval());
    }
}
