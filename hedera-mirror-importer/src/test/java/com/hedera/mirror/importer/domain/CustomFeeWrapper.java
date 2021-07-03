package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import lombok.Data;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.importer.util.EntityIdEndec;

@Data
public class CustomFeeWrapper {

    public static final String SELECT_QUERY = "select * from custom_fee";
    public static final RowMapper<CustomFeeWrapper> ROW_MAPPER = new DataClassRowMapper<>(CustomFeeWrapper.class);

    private final CustomFee customFee;

    public CustomFeeWrapper(Long amount, Long amountDenominator, Long collectorAccountId, long createdTimestamp,
            Long denominatingTokenId, Long maximumAmount, Long minimumAmount, long tokenId) {
        customFee = new CustomFee();
        customFee.setAmount(amount);
        customFee.setAmountDenominator(amountDenominator);

        if (collectorAccountId != null) {
            customFee.setCollectorAccountId(EntityIdEndec.decode(collectorAccountId, EntityTypeEnum.ACCOUNT));
        }

        if (denominatingTokenId != null) {
            customFee.setDenominatingTokenId(EntityIdEndec.decode(denominatingTokenId, EntityTypeEnum.TOKEN));
        }

        customFee.setMaximumAmount(maximumAmount);
        customFee.setMinimumAmount(minimumAmount);
        customFee.setId(new CustomFee.Id(createdTimestamp, EntityIdEndec.decode(tokenId, EntityTypeEnum.TOKEN)));
    }
}
