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
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.FixedFee;
import com.hedera.mirror.common.domain.transaction.FractionalFee;
import com.hedera.mirror.common.domain.transaction.RoyaltyFee;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import org.postgresql.util.PGobject;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.core.RowMapper;

public class CustomFeeConverter {

    public static final CustomFeeConverter INSTANCE = new CustomFeeConverter();

    private static final String allCollectorsAreExempt = "all_collectors_are_exempt";
    private static final String amount = "amount";
    private static final String amountDenominator = "amount_denominator";
    private static final String collectorAccountId = "collector_account_id";
    private static final String denominatingTokenId = "denominating_token_id";
    private static final String fallbackFee = "fallback_fee";
    private static final String maximumAmount = "maximum_amount";
    private static final String minimumAmount = "minimum_amount";
    private static final String netOfTransfers = "net_of_transfers";
    private static final String royaltyDenominator = "royalty_denominator";
    private static final String royaltyNumerator = "royalty_numerator";

    public final RowMapper<CustomFee> rowMapper = (rs, rowNum) -> {
        long createdTimestamp = rs.getLong(1);
        long token_id = rs.getLong(2);
        List<FixedFee> fixedFees = convertFixedFees(rs.getObject(3, PGobject.class));
        List<FractionalFee> fractionalFees = convertFractionalFees(rs.getObject(4, PGobject.class));
        List<RoyaltyFee> royaltyFees = convertRoyaltyFees(rs.getObject(5, PGobject.class));
        var timestampRange = convertRange(rs.getObject(6, PGobject.class));
        return CustomFee.builder()
                .createdTimestamp(createdTimestamp)
                .tokenId(token_id)
                .fixedFees(fixedFees)
                .fractionalFees(fractionalFees)
                .royaltyFees(royaltyFees)
                .timestampRange(timestampRange)
                .build();
    };

    @SneakyThrows
    private static List<FixedFee> convertFixedFees(PGobject pgobject) {
        if (pgobject.isNull()) {
            return null;
        }
        var fixedFees = new ArrayList<FixedFee>();
        var jsonArray = new JSONArray(pgobject.getValue());
        for (int i = 0; i < jsonArray.length(); i++) {
            var item = jsonArray.getJSONObject(i);
            fixedFees.add(FixedFee.builder()
                    .allCollectorsAreExempt(item.getBoolean(allCollectorsAreExempt))
                    .amount(item.getLong(amount))
                    .collectorAccountId(getCollectorAccountId(item))
                    .denominatingTokenId(getDenominatingTokenId(item))
                    .build());
        }
        return fixedFees;
    }

    @SneakyThrows
    private static List<FractionalFee> convertFractionalFees(PGobject pgobject) {
        if (pgobject.isNull()) {
            return null;
        }
        var fractionalFees = new ArrayList<FractionalFee>();
        var jsonArray = new JSONArray(pgobject.getValue());
        for (int i = 0; i < jsonArray.length(); i++) {
            var item = jsonArray.getJSONObject(i);
            fractionalFees.add(FractionalFee.builder()
                    .allCollectorsAreExempt(item.getBoolean(allCollectorsAreExempt))
                    .amount(item.getLong(amount))
                    .amountDenominator(item.getLong(amountDenominator))
                    .netOfTransfers(item.getBoolean(netOfTransfers))
                    .maximumAmount(item.getLong(maximumAmount))
                    .minimumAmount(item.getLong(minimumAmount))
                    .collectorAccountId(getCollectorAccountId(item))
                    .build());
        }
        return fractionalFees;
    }

    @SneakyThrows
    private static List<RoyaltyFee> convertRoyaltyFees(PGobject pgobject) {
        if (pgobject.isNull()) {
            return null;
        }
        var royaltyFees = new ArrayList<RoyaltyFee>();
        var jsonArray = new JSONArray(pgobject.getValue());
        for (int i = 0; i < jsonArray.length(); i++) {
            var item = jsonArray.getJSONObject(i);
            var builder = RoyaltyFee.builder()
                    .allCollectorsAreExempt(item.getBoolean(allCollectorsAreExempt))
                    .collectorAccountId(getCollectorAccountId(item))
                    .royaltyDenominator(item.getLong(royaltyDenominator))
                    .royaltyNumerator(item.getLong(royaltyNumerator));

            if (item.has("fallback_fee")) {
                var fallBackFee = item.getJSONObject(fallbackFee);
                builder.fallbackFee(FixedFee.builder()
                        .allCollectorsAreExempt(fallBackFee.getBoolean(allCollectorsAreExempt))
                        .amount(fallBackFee.getLong(amount))
                        .collectorAccountId(getCollectorAccountId(fallBackFee))
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
        return item.isNull(collectorAccountId) ? null : EntityId.of(item.getLong(collectorAccountId), ACCOUNT);
    }

    @SneakyThrows
    private static EntityId getDenominatingTokenId(JSONObject item) {
        return item.isNull(denominatingTokenId) ? null : EntityId.of(item.getLong(denominatingTokenId), TOKEN);
    }
}
