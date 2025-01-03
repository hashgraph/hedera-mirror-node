/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.A;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.B;
import static com.hedera.services.store.contracts.precompile.FungibleTokenTransferTest.NON_FUNGIBLE;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NftExchangeTest {

    @Test
    void createsExpectedCryptoTransfer() {
        final var nftExchange = new NftExchange(1L, NON_FUNGIBLE, A, B);
        assertFalse(nftExchange.isApproval());
        assertEquals(NON_FUNGIBLE, nftExchange.getTokenType());
        assertTrue(nftExchange.asGrpc().hasSenderAccountID());
        assertEquals(1L, nftExchange.getSerialNo());
        assertTrue(NftExchange.fromApproval(1L, NON_FUNGIBLE, A, B).isApproval());
    }
}
