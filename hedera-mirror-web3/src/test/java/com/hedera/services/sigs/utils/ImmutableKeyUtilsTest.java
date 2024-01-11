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

package com.hedera.services.sigs.utils;

import static com.hedera.services.sigs.utils.ImmutableKeyUtils.signalsKeyRemoval;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import org.junit.jupiter.api.Test;

class ImmutableKeyUtilsTest {
    @Test
    void recognizesSentinelKey() {
        assertFalse(signalsKeyRemoval(Key.getDefaultInstance()));
        assertFalse(signalsKeyRemoval(Key.newBuilder()
                .setThresholdKey(ThresholdKey.getDefaultInstance())
                .build()));
        assertTrue(signalsKeyRemoval(
                Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build()));
    }
}
