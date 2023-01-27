package com.hedera.mirror.common.domain.token;

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

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.UpsertColumn;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE) // For Builder
@Builder
@Data
@Entity
@NoArgsConstructor
@TypeDef(
        name = "pgsql_enum",
        typeClass = PostgreSQLEnumType.class
)
@Upsertable
public class Token {
    @EmbeddedId
    @JsonUnwrapped
    private TokenId tokenId;

    @Column(updatable = false)
    private Long createdTimestamp;

    @Column(updatable = false)
    private Integer decimals;

    @ToString.Exclude
    private byte[] feeScheduleKey;

    @Column(updatable = false)
    private Boolean freezeDefault;

    @ToString.Exclude
    private byte[] freezeKey;

    @Column(updatable = false)
    private Long initialSupply;

    @ToString.Exclude
    private byte[] kycKey;

    @Column(updatable = false)
    private long maxSupply;

    private long modifiedTimestamp;

    private String name;

    @ToString.Exclude
    private byte[] pauseKey;

    @Enumerated(EnumType.STRING)
    @Type(type = "pgsql_enum")
    private TokenPauseStatusEnum pauseStatus;

    @ToString.Exclude
    private byte[] supplyKey;

    @Column(updatable = false)
    @Enumerated(EnumType.STRING)
    @Type(type = "pgsql_enum")
    private TokenSupplyTypeEnum supplyType;

    private String symbol;

    @UpsertColumn(coalesce = "case when {0} >= 0 then {0} else e_{0} + coalesce({0}, {1}) end")
    private Long totalSupply; // Increment with initialSupply and mint amounts, decrement with burn amount

    @Convert(converter = AccountIdConverter.class)
    private EntityId treasuryAccountId;

    @Column(updatable = false)
    @Enumerated(EnumType.STRING)
    @Type(type = "pgsql_enum")
    private TokenTypeEnum type;

    @ToString.Exclude
    private byte[] wipeKey;

    public static Token of(EntityId tokenId) {
        Token token = new Token();
        token.setTokenId(new TokenId(tokenId));
        return token;
    }

    public void setInitialSupply(Long initialSupply) {
        this.initialSupply = initialSupply;

        // default totalSupply to initial supply
        totalSupply = initialSupply;
    }

    public void setName(String name) {
        this.name = DomainUtils.sanitize(name);
    }

    public void setSymbol(String symbol) {
        this.symbol = DomainUtils.sanitize(symbol);
    }
}
