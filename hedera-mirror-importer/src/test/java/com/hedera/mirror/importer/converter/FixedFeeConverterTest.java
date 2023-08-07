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

package com.hedera.mirror.importer.converter;

import static com.hedera.mirror.importer.converter.FixedFeeConverter.INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.FixedFee;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

class FixedFeeConverterTest {

    @Test
    void testNoFee() {
        assertThat(INSTANCE.convert(null)).isEmpty();
        assertThat(INSTANCE.convert(new PGobject())).isEmpty();
    }

    @Test
    @SneakyThrows
    void testConvert() {
        var pgObject = new PGobject();
        pgObject.setValue(
                """
            [{
                "all_collectors_are_exempt": true,
                "amount": 106,
                "collector_account_id": 102,
                "denominating_token_id": 101
            }]
        """);

        var expected = FixedFee.builder()
                .allCollectorsAreExempt(true)
                .amount(106L)
                .collectorAccountId(EntityId.of(102, EntityType.ACCOUNT))
                .denominatingTokenId(EntityId.of(101, EntityType.TOKEN))
                .build();

        assertThat(INSTANCE.convert(pgObject)).containsExactly(expected);
    }
}
