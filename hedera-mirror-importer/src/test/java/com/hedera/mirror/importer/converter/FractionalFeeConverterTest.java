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

import static com.hedera.mirror.importer.converter.FractionalFeeConverter.INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.FractionalFee;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

class FractionalFeeConverterTest {

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
                    "all_collectors_are_exempt":true,
                    "amount": 106,
                    "collector_account_id":2,
                    "denominator":12,
                    "maximum_amount":13,
                    "minimum_amount":1,
                    "net_of_transfers":true
                }]
                """);

        var expected = FractionalFee.builder()
                .allCollectorsAreExempt(true)
                .amount(106L)
                .collectorAccountId(EntityId.of(2, EntityType.ACCOUNT))
                .denominator(12L)
                .maximumAmount(13L)
                .minimumAmount(1)
                .netOfTransfers(true)
                .build();

        assertThat(INSTANCE.convert(pgObject)).containsExactly(expected);
    }
}
