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

package com.hedera.services.txns.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;

import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenListChecksTest {

    @Mock
    Predicate<Key> adminKeyRemoval;

    @Test
    void permitsAdminKeyRemoval() {
        TokenListChecks.adminKeyRemoval = adminKeyRemoval;
        given(adminKeyRemoval.test(any())).willReturn(true);

        final var validity = TokenListChecks.checkKeys(
                true, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance());

        assertEquals(OK, validity);

        TokenListChecks.adminKeyRemoval = ImmutableKeyUtils::signalsKeyRemoval;
    }

    @Test
    void checksInvalidFeeScheduleKey() {
        final var invalidKeyList1 = KeyList.newBuilder().build();
        final var invalidFeeScheduleKey =
                Key.newBuilder().setKeyList(invalidKeyList1).build();

        final var validity = TokenListChecks.checkKeys(
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                false, Key.getDefaultInstance(),
                true, invalidFeeScheduleKey,
                false, Key.getDefaultInstance());

        assertEquals(INVALID_CUSTOM_FEE_SCHEDULE_KEY, validity);
    }

    @Test
    void checksInvalidPauseKey() {
        final var invalidKeyList1 = KeyList.newBuilder().build();
        final var invalidPauseKey = Key.newBuilder().setKeyList(invalidKeyList1).build();

        final var validity = TokenListChecks.checkKeys(
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                false,
                Key.getDefaultInstance(),
                true,
                invalidPauseKey);

        assertEquals(INVALID_PAUSE_KEY, validity);
    }
}
