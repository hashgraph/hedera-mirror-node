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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.validation.constraints.Min;
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
public class Token {

    @EmbeddedId
    private Token.Id tokenId;

    private long createdTimestamp;

    private long decimals;

    private boolean freezeDefault;

    @ToString.Exclude
    private byte[] freezeKey;

    @Column(name = "freeze_key_ed25519_hex")
    @ToString.Exclude
    private String freezeKeyEd25519Hex;

    private long initialSupply;

    private long totalSupply; // Increment with initialSupply and mint amounts, decrement with burn amount

    @ToString.Exclude
    private byte[] kycKey;

    @Column(name = "kyc_key_ed25519_hex")
    @ToString.Exclude
    private String kycKeyEd25519Hex;

    @Min(0)
    private long maxSSupply;

    private long modifiedTimestamp;

    private String name;

    @ToString.Exclude
    private byte[] supplyKey;

    @Column(name = "supply_key_ed25519_hex")
    @ToString.Exclude
    private String supplyKeyEd25519Hex;

    @Enumerated(EnumType.ORDINAL)
    private TokenSupplyTypeEnum supplyType;

    private String symbol;

    @Convert(converter = AccountIdConverter.class)
    private EntityId treasuryAccountId;

    @Enumerated(EnumType.ORDINAL)
    private TokenTypeEnum type;

    @ToString.Exclude
    private byte[] wipeKey;

    @Column(name = "wipe_key_ed25519_hex")
    @ToString.Exclude
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
