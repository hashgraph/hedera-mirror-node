/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.jproto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JKeyTest {

    @Test
    void canGetPrimitiveKeyForEd25519OrSecp256k1() {
        final var mockEd25519 = new JEd25519Key("01234578901234578901234578901".getBytes());
        final var mockSecp256k1 = new JECDSASecp256k1Key("012345789012345789012345789012".getBytes());

        assertEquals(0, mockEd25519.getECDSASecp256k1Key().length);
        assertEquals(0, mockSecp256k1.getEd25519().length);
        assertEquals(0, mockSecp256k1.getECDSA384().length);
        assertEquals(0, mockSecp256k1.getRSA3072().length);
    }
}
