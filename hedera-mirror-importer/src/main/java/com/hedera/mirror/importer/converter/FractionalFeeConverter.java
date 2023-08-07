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

import com.hedera.mirror.common.domain.token.FractionalFee;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import org.postgresql.util.PGobject;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.core.convert.converter.Converter;

@SuppressWarnings("java:S6548")
public class FractionalFeeConverter extends AbstractFeeConverter implements Converter<PGobject, List<FractionalFee>> {

    public static final FractionalFeeConverter INSTANCE = new FractionalFeeConverter();

    @SneakyThrows
    @Override
    public List<FractionalFee> convert(PGobject source) {
        if (source == null || source.isNull()) {
            return Collections.emptyList();
        }

        var fractionalFees = new ArrayList<FractionalFee>();
        var jsonArray = new JSONArray(source.getValue());
        for (int i = 0; i < jsonArray.length(); i++) {
            var item = jsonArray.getJSONObject(i);
            fractionalFees.add(FractionalFee.builder()
                    .allCollectorsAreExempt(item.getBoolean(ALL_COLLECTORS_ARE_EXEMPT))
                    .amount(item.getLong(AMOUNT))
                    .denominator(item.getLong(DENOMINATOR))
                    .netOfTransfers(item.getBoolean(NET_OF_TRANSFERS))
                    .maximumAmount(item.getLong(MAXIMUM_AMOUNT))
                    .minimumAmount(item.getLong(MINIMUM_AMOUNT))
                    .collectorAccountId(getCollectorAccountId(item))
                    .build());
        }
        return fractionalFees;
    }
}
