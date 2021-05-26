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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.EntityIdSerializer;
import com.hedera.mirror.importer.converter.TokenIdConverter;
import com.hedera.mirror.importer.util.Utility;

@Data
@Entity
@NoArgsConstructor
@ToString(exclude = {"freezeKey", "kycKey", "supplyKey", "wipeKey"})
public class Token {
    public static final String TEMP_TABLE = "token_temp";
    public static final String TEMP_TO_MAIN_INSERT_SQL = "insert into token select * from " + TEMP_TABLE +
            " where created_timestamp is not null on conflict(token_id) do nothing";
    public static final String TEMP_TO_MAIN_UPDATE_SQL = "update token t set " +
            "freeze_key = coalesce(" + TEMP_TABLE + ".freeze_key, t.freeze_key), " +
            "freeze_key_ed25519_hex = coalesce(" + TEMP_TABLE + ".freeze_key_ed25519_hex, t.freeze_key_ed25519_hex), " +
            "kyc_key =  coalesce(" + TEMP_TABLE + ".kyc_key, t.kyc_key), " +
            "kyc_key_ed25519_hex =  coalesce(" + TEMP_TABLE + ".kyc_key_ed25519_hex, t.kyc_key_ed25519_hex), " +
            "modified_timestamp = " + TEMP_TABLE + ".modified_timestamp, " +
            "name = coalesce(" + TEMP_TABLE + ".name, t.name), " +
            "supply_key = coalesce(" + TEMP_TABLE + ".supply_key, t.supply_key), " +
            "supply_key_ed25519_hex = coalesce(" + TEMP_TABLE + ".supply_key_ed25519_hex, t.supply_key_ed25519_hex), " +
            "symbol = coalesce(" + TEMP_TABLE + ".symbol, t.symbol), " +
            "total_supply = coalesce(" + TEMP_TABLE + ".total_supply, t.total_supply)," +
            "treasury_account_id = coalesce(" + TEMP_TABLE + ".treasury_account_id, t.treasury_account_id), " +
            "wipe_key = coalesce(" + TEMP_TABLE + ".wipe_key, t.wipe_key), " +
            "wipe_key_ed25519_hex = coalesce(" + TEMP_TABLE + ".wipe_key_ed25519_hex, t.wipe_key_ed25519_hex) " +
            "from " + TEMP_TABLE +
            " where t.token_id = " + TEMP_TABLE + ".token_id and " + TEMP_TABLE + ".created_timestamp is null";
    public static final String TEMP_TO_MAIN_UPSERT_SQL = "insert into token select * from " + TEMP_TABLE + " on " +
            "conflict " +
            "(token_id) do update set " +
            "freeze_key = coalesce(excluded.freeze_key, token.freeze_key), " +
            "freeze_key_ed25519_hex = coalesce(excluded.freeze_key_ed25519_hex, token.freeze_key_ed25519_hex), " +
            "kyc_key =  coalesce(excluded.kyc_key, token.kyc_key), " +
            "kyc_key_ed25519_hex =  coalesce(excluded.kyc_key_ed25519_hex, token.kyc_key_ed25519_hex), " +
            "modified_timestamp = excluded.modified_timestamp, " +
            "name = coalesce(excluded.name, ''), " +
            "supply_key = coalesce(excluded.supply_key, token.supply_key), " +
            "supply_key_ed25519_hex = coalesce(excluded.supply_key_ed25519_hex, token.supply_key_ed25519_hex), " +
            "symbol = excluded.symbol, " +
            "total_supply = case when excluded.total_supply > 0 then excluded.total_supply else token.total_supply " +
            "end," +
            "treasury_account_id = excluded.treasury_account_id, " +
            "wipe_key = coalesce(excluded.wipe_key, token.wipe_key), " +
            "wipe_key_ed25519_hex = coalesce(excluded.wipe_key_ed25519_hex, token.wipe_key_ed25519_hex)";

    @EmbeddedId
    @JsonUnwrapped
    private Token.Id tokenId;

    private Long createdTimestamp; // make object nullable to help differentiate between update and missing value

    private long decimals;

    private boolean freezeDefault;

    private byte[] freezeKey;

    @Column(name = "freeze_key_ed25519_hex")
    private String freezeKeyEd25519Hex;

    private long initialSupply;

    private Long totalSupply; // Increment with initialSupply and mint amounts, decrement with burn amount

    private byte[] kycKey;

    @Column(name = "kyc_key_ed25519_hex")
    private String kycKeyEd25519Hex;

    private long modifiedTimestamp;

    private String name;

    private byte[] supplyKey;

    @Column(name = "supply_key_ed25519_hex")
    private String supplyKeyEd25519Hex;

    private String symbol;

    @Convert(converter = AccountIdConverter.class)
    @JsonSerialize(using = EntityIdSerializer.class)
    private EntityId treasuryAccountId;

    private byte[] wipeKey;

    @Column(name = "wipe_key_ed25519_hex")
    private String wipeKeyEd25519Hex;

    public void setInitialSupply(Long initialSupply) {
        this.initialSupply = initialSupply;

        // default totalSupply to initial supply
        totalSupply = initialSupply;
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

    /**
     * Get initial freeze status for an account being associated with this token. If the token does not have a
     * freezeKey, FreezeNotApplicable is returned, if it does account frozen status is set based on freezeDefault.
     * FreezeNotApplicable = 0, Frozen = 1, Unfrozen = 2
     *
     * @return Freeze status code
     */
    @JsonIgnore
    @Transient
    public TokenFreezeStatusEnum getNewAccountFreezeStatus() {
        if (freezeKey == null) {
            return TokenFreezeStatusEnum.NOT_APPLICABLE;
        }

        return freezeDefault ? TokenFreezeStatusEnum.FROZEN : TokenFreezeStatusEnum.UNFROZEN;
    }

    /**
     * Get initial kyc status for an account being associated with this token. If the token does not have a kycKey,
     * KycNotApplicable is returned, if it does account should be set to Revoked as kyc must be performed.
     * KycNotApplicable = 0, Granted = 1, Revoked = 2
     *
     * @return Kyc status code
     */
    @JsonIgnore
    @Transient
    public TokenKycStatusEnum getNewAccountKycStatus() {
        if (kycKey == null) {
            return TokenKycStatusEnum.NOT_APPLICABLE;
        }

        return TokenKycStatusEnum.REVOKED;
    }

    public void setName(String name) {
        this.name = Utility.sanitize(name);
    }

    public void setSymbol(String symbol) {
        this.symbol = Utility.sanitize(symbol);
    }

    @Data
    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = -4595724698253758379L;

        @Convert(converter = TokenIdConverter.class)
        @JsonSerialize(using = EntityIdSerializer.class)
        private EntityId tokenId;
    }
}
