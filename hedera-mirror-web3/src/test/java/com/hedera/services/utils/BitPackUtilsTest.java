/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.utils.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.utils.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BitPackUtilsTest {
    private final int maxAutoAssociations = 123;
    private final int alreadyUsedAutomaticAssociations = 12;
    private int metadata = buildAutomaticAssociationMetaData(maxAutoAssociations, alreadyUsedAutomaticAssociations);

    static int buildAutomaticAssociationMetaData(int maxAutoAssociations, int alreadyUsedAutoAssociations) {
        return (alreadyUsedAutoAssociations << 16) | maxAutoAssociations;
    }

    @Test
    void automaticAssociationsMetaWorks() {
        assertEquals(maxAutoAssociations, getMaxAutomaticAssociationsFrom(metadata));
        assertEquals(alreadyUsedAutomaticAssociations, getAlreadyUsedAutomaticAssociationsFrom(metadata));
    }
}
