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

package com.hedera.mirror.web3.evm.store.accessor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.Entity;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AccessorUtilsTest {
    @Test
    void getExpirationTimestampWhenPresentInEntityReturn() {
        Entity entity = mock(Entity.class);
        long expirationTimestamp = 123L;

        when(entity.getExpirationTimestamp()).thenReturn(expirationTimestamp);

        assertThat(AccessorUtils.getEntityExpiration(entity)).isEqualTo(expirationTimestamp);
    }

    @Test
    void getExpirationTimestampWhenNotPresentInEntityReturnFromCreationAndAutoRenew() {
        Entity entity = mock(Entity.class);
        long createdTimestamp = 123L;
        long autoRenew = 234L;
        when(entity.getExpirationTimestamp()).thenReturn(null);
        when(entity.getCreatedTimestamp()).thenReturn(createdTimestamp);
        when(entity.getAutoRenewPeriod()).thenReturn(autoRenew);

        long expected = createdTimestamp + TimeUnit.SECONDS.toNanos(autoRenew);
        assertThat(AccessorUtils.getEntityExpiration(entity)).isEqualTo(expected);
    }

    @Test
    void getExpirationTimestampWhenNoInformationPresetInEntityReturnDefaultValue() {
        Entity entity = mock(Entity.class);
        when(entity.getExpirationTimestamp()).thenReturn(null);
        when(entity.getCreatedTimestamp()).thenReturn(null);
        when(entity.getAutoRenewPeriod()).thenReturn(null);

        assertThat(AccessorUtils.getEntityExpiration(entity)).isEqualTo(AccessorUtils.DEFAULT_EXPIRY_TIMESTAMP);
    }
}
