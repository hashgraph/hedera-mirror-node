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
import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.EntityIdSerializer;
import com.hedera.mirror.importer.converter.TokenIdConverter;
import com.hedera.mirror.importer.util.Utility;

@Data
@Entity
@Log4j2
@ToString(exclude = {"freezeKey", "kycKey", "supplyKey", "wipeKey"})
public class Token {
    @EmbeddedId
    private Token.Id tokenId;

    private long createdTimestamp;

    private int divisibility;

    private boolean freezeDefault;

    private byte[] freezeKey;

    private Long initialSupply;

//    private Long currentSupply; // Increment with Mint amount, decrement with Burn amount

    private boolean kycDefault;

    private byte[] kycKey;

    private long modifiedTimestamp;

    private String name;

    private byte[] supplyKey;

    private String symbol;

    @Convert(converter = AccountIdConverter.class)
    private EntityId treasuryAccountId;

    private byte[] wipeKey;

    @Column(name = "ed25519_freeze_key")
    private String ed25519FreezeKey;

    @Column(name = "ed25519_kyc_key")
    private String ed25519KycKey;

    @Column(name = "ed25519_supply_key")
    private String ed25519SupplyKey;

    @Column(name = "ed25519_wipe_key")
    private String ed25519WipeKey;

    public void setFreezeKey(byte[] key) {
        freezeKey = key;
        ed25519FreezeKey = convertByteKeyToHex(key);
    }

    public void setKycKey(byte[] key) {
        kycKey = key;
        ed25519KycKey = convertByteKeyToHex(key);
    }

    public void setSupplyKey(byte[] key) {
        supplyKey = key;
        ed25519SupplyKey = convertByteKeyToHex(key);
    }

    public void setWipeKey(byte[] key) {
        wipeKey = key;
        ed25519WipeKey = convertByteKeyToHex(key);
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
