/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
