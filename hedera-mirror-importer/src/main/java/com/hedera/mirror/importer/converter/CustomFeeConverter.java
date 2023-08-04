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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.FallbackFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.postgresql.util.PGobject;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.core.RowMapper;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomFeeConverter {

    private static final String ALL_COLLECTORS_ARE_EXEMPT = "all_collectors_are_exempt";
    private static final String AMOUNT = "amount";
    private static final String COLLECTOR_ACCOUNT_ID = "collector_account_id";
    private static final String DENOMINATING_TOKEN_ID = "denominating_token_id";
    private static final String DENOMINATOR = "denominator";
    private static final String FALLBACK_FEE = "fallback_fee";
    private static final String MAXIMUM_AMOUNT = "maximum_amount";
    private static final String MINIMUM_AMOUNT = "minimum_amount";
    private static final String NET_OF_TRANSFERS = "net_of_transfers";

    public static final RowMapper<CustomFee> rowMapper = (rs, rowNum) -> {
        List<FixedFee> fixedFees = convertFixedFees(rs.getObject(1, PGobject.class));
        List<FractionalFee> fractionalFees = convertFractionalFees(rs.getObject(2, PGobject.class));
        List<RoyaltyFee> royaltyFees = convertRoyaltyFees(rs.getObject(3, PGobject.class));
        var timestampRange = convertRange(rs.getObject(4, PGobject.class));
        long tokenId = rs.getLong(5);
        return CustomFee.builder()
                .fixedFees(fixedFees)
                .fractionalFees(fractionalFees)
                .royaltyFees(royaltyFees)
                .timestampRange(timestampRange)
                .tokenId(tokenId)
                .build();
    };

    @SneakyThrows
    private static List<FixedFee> convertFixedFees(PGobject pgobject) {
        if (pgobject.isNull()) {
            return Collections.emptyList();
        }
        var fixedFees = new ArrayList<FixedFee>();
        var jsonArray = new JSONArray(pgobject.getValue());
        for (int i = 0; i < jsonArray.length(); i++) {
            var item = jsonArray.getJSONObject(i);
            fixedFees.add(FixedFee.builder()
                    .allCollectorsAreExempt(item.getBoolean(ALL_COLLECTORS_ARE_EXEMPT))
                    .amount(item.getLong(AMOUNT))
                    .collectorAccountId(getCollectorAccountId(item))
                    .denominatingTokenId(getDenominatingTokenId(item))
                    .build());
        }
        return fixedFees;
    }

    @SneakyThrows
    private static List<FractionalFee> convertFractionalFees(PGobject pgobject) {
        if (pgobject.isNull()) {
            return Collections.emptyList();
        }
        var fractionalFees = new ArrayList<FractionalFee>();
        var jsonArray = new JSONArray(pgobject.getValue());
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

    @SneakyThrows
    private static List<RoyaltyFee> convertRoyaltyFees(PGobject pgobject) {
        if (pgobject.isNull()) {
            return Collections.emptyList();
        }
        var royaltyFees = new ArrayList<RoyaltyFee>();
        var jsonArray = new JSONArray(pgobject.getValue());
        for (int i = 0; i < jsonArray.length(); i++) {
            var item = jsonArray.getJSONObject(i);
            var builder = RoyaltyFee.builder()
                    .allCollectorsAreExempt(item.getBoolean(ALL_COLLECTORS_ARE_EXEMPT))
                    .amount(item.getLong(AMOUNT))
                    .collectorAccountId(getCollectorAccountId(item))
                    .denominator(item.getLong(DENOMINATOR));

            if (item.has(FALLBACK_FEE)) {
                var fallBackFee = item.getJSONObject(FALLBACK_FEE);
                builder.fallbackFee(FallbackFee.builder()
                        .amount(fallBackFee.getLong(AMOUNT))
                        .denominatingTokenId(getDenominatingTokenId(fallBackFee))
                        .build());
            }
            royaltyFees.add(builder.build());
        }
        return royaltyFees;
    }

    private static Range<Long> convertRange(PGobject pgobject) {
        return PostgreSQLGuavaRangeType.longRange(pgobject.getValue());
    }

    @SneakyThrows
    private static EntityId getCollectorAccountId(JSONObject item) {
        return item.isNull(COLLECTOR_ACCOUNT_ID) ? null : EntityId.of(item.getLong(COLLECTOR_ACCOUNT_ID), ACCOUNT);
    }

    @SneakyThrows
    private static EntityId getDenominatingTokenId(JSONObject item) {
        return item.isNull(DENOMINATING_TOKEN_ID) ? null : EntityId.of(item.getLong(DENOMINATING_TOKEN_ID), TOKEN);
    }
}
