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

package com.hedera.services.utils;

import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hedera.services.utils.MiscUtils.perm64;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class})
class MiscUtilsTest {

    @Test
    void asFcKeyUncheckedTranslatesExceptions() {
        final var key = Key.getDefaultInstance();
        assertThrows(IllegalArgumentException.class, () -> MiscUtils.asFcKeyUnchecked(key));
    }

    @Test
    void asFcKeyReturnsEmptyOnUnparseableKey() {
        final var key = Key.getDefaultInstance();
        assertTrue(asUsableFcKey(key).isEmpty());
    }

    @Test
    void asFcKeyReturnsEmptyOnEmptyKey() {
        assertTrue(asUsableFcKey(Key.newBuilder()
                        .setKeyList(KeyList.getDefaultInstance())
                        .build())
                .isEmpty());
    }

    @Test
    void asFcKeyReturnsEmptyOnInvalidKey() {
        assertTrue(asUsableFcKey(Key.newBuilder()
                        .setEd25519(ByteString.copyFrom("1".getBytes()))
                        .build())
                .isEmpty());
    }

    @Test
    void perm64Test() {
        assertEquals(0L, perm64(0L));
        assertEquals(-4328535976359616544L, perm64(1L));
        assertEquals(2657016865369639288L, perm64(7L));
    }
}
