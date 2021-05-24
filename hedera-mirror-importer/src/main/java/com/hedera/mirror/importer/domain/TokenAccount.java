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

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serializable;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.EntityIdSerializer;
import com.hedera.mirror.importer.converter.TokenIdConverter;

@Data
@Entity
@NoArgsConstructor
public class TokenAccount {
    public static final String TEMP_TABLE = "token_account_temp";
    public static final String TempToMainUpdateSql = "insert into token_account select account_id, " +
            "coalesce(associated, true) as associated, " +
            "created_timestamp, coalesce(freeze_status, getNewAccountFreezeStatus(token_id)) as " +
            "freeze_status, coalesce(kyc_status, getNewAccountKycStatus(token_id)) as kyc_status, " +
            "modified_timestamp, token_id from " + TEMP_TABLE + " on " +
            "conflict(token_id, account_id) do update set " +
            "modified_timestamp = excluded.modified_timestamp," +
            "associated = coalesce(excluded.associated, token_account.associated), " +
            "freeze_status = coalesce(excluded.freeze_status, token_account.freeze_status)," +
            "kyc_status = coalesce(excluded.kyc_status, token_account.kyc_status)";

    @EmbeddedId
    @JsonUnwrapped
    private TokenAccount.Id id;

    private Boolean associated;

    private long createdTimestamp;

    @Enumerated(EnumType.ORDINAL)
    private TokenFreezeStatusEnum freezeStatus;

    @Enumerated(EnumType.ORDINAL)
    private TokenKycStatusEnum kycStatus;

    private long modifiedTimestamp;

    public TokenAccount(EntityId tokenId, EntityId accountId) {
        id = new TokenAccount.Id(tokenId, accountId);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Embeddable
    public static class Id implements Serializable {
        private static final long serialVersionUID = -4069569824910871771L;

        @Convert(converter = TokenIdConverter.class)
        @JsonSerialize(using = EntityIdSerializer.class)
        private EntityId tokenId;

        @Convert(converter = AccountIdConverter.class)
        @JsonSerialize(using = EntityIdSerializer.class)
        private EntityId accountId;
    }
}
