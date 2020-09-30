package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.EntityIdSerializer;
import com.hedera.mirror.importer.converter.TokenIdConverter;
import com.hedera.mirror.importer.util.Utility;

@Builder
@Data
@Entity
@Log4j2
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"freezeKey", "kycKey", "supplyKey", "wipeKey"})
public class Token {
    @EmbeddedId
    private Token.Id tokenId;

    private long createdTimestamp;

    private int decimals;

    private boolean freezeDefault;

    private byte[] freezeKey;

    private Long initialSupply;

    private Long totalSupply; // Increment with initialSupply and mint amounts, decrement with burn amount

    private byte[] kycKey;

    private long modifiedTimestamp;

    private String name;

    private byte[] supplyKey;

    private String symbol;

    @Convert(converter = AccountIdConverter.class)
    private EntityId treasuryAccountId;

    private byte[] wipeKey;

    @Column(name = "freeze_key_ed25519_hex")
    private String freezeKeyEd25519Hex;

    @Column(name = "kyc_key_ed25519_hex")
    private String kycKeyEd25519Hex;

    @Column(name = "supply_key_ed25519_hex")
    private String supplyKeyEd25519Hex;

    @Column(name = "wipe_key_ed25519_hex")
    private String wipeKeyEd25519Hex;

    public void setInitialSupply(Long initialSupply) {
        this.initialSupply = initialSupply;

        // default totalSupply to initial supply
        totalSupply = initialSupply;
    }

    public void setFreezeKey(byte[] key) {
        freezeKey = key;
        freezeKeyEd25519Hex = convertByteKeyToHex(key);
    }

    public void setKycKey(byte[] key) {
        kycKey = key;
        kycKeyEd25519Hex = convertByteKeyToHex(key);
    }

    public void setSupplyKey(byte[] key) {
        supplyKey = key;
        supplyKeyEd25519Hex = convertByteKeyToHex(key);
    }

    public void setWipeKey(byte[] key) {
        wipeKey = key;
        wipeKeyEd25519Hex = convertByteKeyToHex(key);
    }

    // FreezeNotApplicable = 0, Frozen = 1, Unfrozen = 2
    // If the token does not have Freeze key, FreezeNotApplicable is returned, if not take value of freezeDefault

    /**
     * Get initial freeze status for an account being associated with this token. If the token does not have a
     * freezeKey, FreezeNotApplicable is returned, if it does account frozen status is set based on freezeDefault.
     * FreezeNotApplicable = 0, Frozen = 1, Unfrozen = 2
     *
     * @return Freeze status code
     */
    public int getNewAccountFreezeStatus() {
        if (freezeKey == null) {
            return TokenFreezeStatus.FreezeNotApplicable_VALUE;
        }

        return freezeDefault ? TokenFreezeStatus.Frozen_VALUE : TokenFreezeStatus.Unfrozen_VALUE;
    }

    /**
     * Get initial kyc status for an account being associated with this token. If the token does not have a kycKey,
     * KycNotApplicable is returned, if it does account should be set to Revoked as kyc must be performed.
     * KycNotApplicable = 0, Granted = 1, Revoked = 2
     *
     * @return Kyc status code
     */
    public int getNewAccountKycStatus() {
        if (kycKey == null) {
            return TokenKycStatus.KycNotApplicable_VALUE;
        }

        return TokenKycStatus.Revoked_VALUE;
    }

    private String convertByteKeyToHex(byte[] key) {
        try {
            return Utility.protobufKeyToHexIfEd25519OrNull(key);
        } catch (Exception e) {
            log.error("Invalid ED25519 key could not be translated to hex text for entity {}. Field " +
                    "will be nulled", tokenId, e);
            return null;
        }
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
