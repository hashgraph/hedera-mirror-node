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
import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.NullableStringSerializer;
import com.hedera.mirror.importer.util.Utility;

@Data
@Entity
@NoArgsConstructor
@TypeDef(
        name = "pgsql_enum",
        typeClass = PostgreSQLEnumType.class
)
@ToString(exclude = {"feeScheduleKey", "feeScheduleKeyEd25519Hex", "freezeKey", "freezeKeyEd25519Hex",
        "kycKey", "kycKeyEd25519Hex", "pauseKey25519Hex", "pauseKey", "supplyKey", "supplyKeyEd25519Hex", "wipeKey",
        "wipeKeyEd25519Hex"})
public class Token {
    @EmbeddedId
    @JsonUnwrapped
    private TokenId tokenId;

    private Long createdTimestamp;

    private Integer decimals;

    private byte[] feeScheduleKey;

    @Column(name = "fee_schedule_key_ed25519_hex")
    @JsonSerialize(using = NullableStringSerializer.class)
    private String feeScheduleKeyEd25519Hex;

    private Boolean freezeDefault;

    private byte[] freezeKey;

    @Column(name = "freeze_key_ed25519_hex")
    @JsonSerialize(using = NullableStringSerializer.class)
    private String freezeKeyEd25519Hex;

    private Long initialSupply;

    private byte[] kycKey;

    @Column(name = "kyc_key_ed25519_hex")
    @JsonSerialize(using = NullableStringSerializer.class)
    private String kycKeyEd25519Hex;

    private long maxSupply;

    private long modifiedTimestamp;

    private byte[] pauseKey;

    @Column(name = "pause_key_ed25519_hex")
    @JsonSerialize(using = NullableStringSerializer.class)
    private String pauseKeyEd25519Hex;

    @Enumerated(EnumType.STRING)
    @Type(type = "pgsql_enum")
    private TokenPauseStatusEnum pauseStatus;

    private String name;

    private byte[] supplyKey;

    @Column(name = "supply_key_ed25519_hex")
    @JsonSerialize(using = NullableStringSerializer.class)
    private String supplyKeyEd25519Hex;

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

    private byte[] wipeKey;

    @Column(name = "wipe_key_ed25519_hex")
    @JsonSerialize(using = NullableStringSerializer.class)
    private String wipeKeyEd25519Hex;

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

    public void setFeeScheduleKey(byte[] key) {
        feeScheduleKey = key;
        feeScheduleKeyEd25519Hex = Utility.convertSimpleKeyToHex(key);
    }

    public void setFreezeKey(byte[] key) {
        freezeKey = key;
        freezeKeyEd25519Hex = Utility.convertSimpleKeyToHex(key);
    }

    public void setKycKey(byte[] key) {
        kycKey = key;
        kycKeyEd25519Hex = Utility.convertSimpleKeyToHex(key);
    }

    public void setSupplyKey(byte[] key) {
        supplyKey = key;
        supplyKeyEd25519Hex = Utility.convertSimpleKeyToHex(key);
    }

    public void setWipeKey(byte[] key) {
        wipeKey = key;
        wipeKeyEd25519Hex = Utility.convertSimpleKeyToHex(key);
    }

    public void setName(String name) {
        this.name = Utility.sanitize(name);
    }

    public void setSymbol(String symbol) {
        this.symbol = Utility.sanitize(symbol);
    }
}
