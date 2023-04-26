/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.domain;

import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import lombok.Data;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;

@Data
public class CustomFeeWrapper {

    public static final String SELECT_QUERY = "select * from custom_fee";
    public static final RowMapper<CustomFeeWrapper> ROW_MAPPER = new DataClassRowMapper<>(CustomFeeWrapper.class);

    private final CustomFee customFee;

    public CustomFeeWrapper(
            Long amount,
            Long amountDenominator,
            Long collectorAccountId,
            long createdTimestamp,
            Long denominatingTokenId,
            Long maximumAmount,
            long minimumAmount,
            Boolean netOfTransfers,
            Long royaltyDenominator,
            Long royaltyNumerator,
            long tokenId,
            Boolean allCollectorsAreExempt) {
        customFee = new CustomFee();
        customFee.setAmount(amount);
        customFee.setAmountDenominator(amountDenominator);

        if (collectorAccountId != null) {
            customFee.setCollectorAccountId(EntityIdEndec.decode(collectorAccountId, EntityType.ACCOUNT));
        }

        if (denominatingTokenId != null) {
            customFee.setDenominatingTokenId(EntityIdEndec.decode(denominatingTokenId, EntityType.TOKEN));
        }

        customFee.setMaximumAmount(maximumAmount);
        customFee.setMinimumAmount(minimumAmount);
        customFee.setNetOfTransfers(netOfTransfers);
        customFee.setRoyaltyDenominator(royaltyDenominator);
        customFee.setRoyaltyNumerator(royaltyNumerator);
        customFee.setId(new CustomFee.Id(createdTimestamp, EntityIdEndec.decode(tokenId, EntityType.TOKEN)));

        if (allCollectorsAreExempt != null) {
            customFee.setAllCollectorsAreExempt(allCollectorsAreExempt);
        }
    }
}
