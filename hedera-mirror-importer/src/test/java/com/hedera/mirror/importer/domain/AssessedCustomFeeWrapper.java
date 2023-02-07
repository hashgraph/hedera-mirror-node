package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.sql.SQLException;
import java.util.Arrays;

import com.hedera.mirror.common.domain.transaction.AssessedCustomFee;

import lombok.Data;
import org.postgresql.jdbc.PgArray;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;

@Data
public class AssessedCustomFeeWrapper {

    public static final String SELECT_QUERY = "select * from assessed_custom_fee";

    public static final RowMapper<AssessedCustomFeeWrapper> ROW_MAPPER =
            new DataClassRowMapper<>(AssessedCustomFeeWrapper.class);

    private final AssessedCustomFee assessedCustomFee;

    public AssessedCustomFeeWrapper(long amount, long collectorAccountId, PgArray effectivePayerAccountIds,
                                    Long tokenId, long consensusTimestamp, Long payerAccountId) throws SQLException {
        assessedCustomFee = new AssessedCustomFee();
        assessedCustomFee.setAmount(amount);
        assessedCustomFee.setId(new AssessedCustomFee.Id(
                EntityIdEndec.decode(collectorAccountId, EntityType.ACCOUNT), consensusTimestamp));
        assessedCustomFee.setPayerAccountId(EntityIdEndec.decode(payerAccountId, EntityType.ACCOUNT));

        if (effectivePayerAccountIds != null) {
            Long[] payers = (Long[]) effectivePayerAccountIds.getArray();
            assessedCustomFee.setEffectivePayerAccountIds(Arrays.asList(payers));
        }

        if (tokenId != null) {
            assessedCustomFee.setTokenId(EntityIdEndec.decode(tokenId, EntityType.TOKEN));
        }
    }
}
