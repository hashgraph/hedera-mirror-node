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
import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
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

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.util.Utility;

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

    private Long createdTimestamp;

    private Integer decimals;

    @ToString.Exclude
    private byte[] feeScheduleKey;

    private Boolean freezeDefault;

    @ToString.Exclude
    private byte[] freezeKey;

    private Long initialSupply;

    @ToString.Exclude
    private byte[] kycKey;

    private long maxSupply;

    private long modifiedTimestamp;

    @ToString.Exclude
    private byte[] pauseKey;

    @Enumerated(EnumType.STRING)
    @Type(type = "pgsql_enum")
    private TokenPauseStatusEnum pauseStatus;

    private String name;

    @ToString.Exclude
    private byte[] supplyKey;

    @Enumerated(EnumType.STRING)
    @Type(type = "pgsql_enum")
    private TokenSupplyTypeEnum supplyType;

    private String symbol;

    private Long totalSupply; // Increment with initialSupply and mint amounts, decrement with burn amount

    @Convert(converter = AccountIdConverter.class)
    private EntityId treasuryAccountId;

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
        this.name = Utility.sanitize(name);
    }

    public void setSymbol(String symbol) {
        this.symbol = Utility.sanitize(symbol);
    }
}
