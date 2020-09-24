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

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import lombok.Data;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.converter.AccountIdConverter;
import com.hedera.mirror.importer.converter.TokenIdConverter;
import com.hedera.mirror.importer.util.Utility;

@Data
@Entity
@Log4j2
@ToString(exclude = {"freezeKey", "kycKey", "supplyKey", "wipeKey"})
public class Token {
    @Id
    @Convert(converter = TokenIdConverter.class)
    private EntityId tokenId;

    private long createdTimestamp;

    private boolean deleted;

    private int divisibility;

    private boolean freezeDefault;

    private byte[] freezeKey;

    private Long initialSupply;

    private boolean kycDefault;

    private byte[] kycKey;

    private long modifiedTimestamp;

    private String name;

    private byte[] supplyKey;

    private String symbol;

    @Convert(converter = AccountIdConverter.class)
    private EntityId treasuryAccountId;

    private byte[] wipeKey;

    private String ed25519PublicFreezeKeyHex;

    private String ed25519PublicKycKeyHex;

    private String ed25519PublicSupplyKeyHex;

    private String ed25519PublicWipeKeyHex;

    public void setFreezeKey(byte[] key) {
        freezeKey = key;
        ed25519PublicFreezeKeyHex = convertByteKeyToHex(key);
    }

    public void setKycKey(byte[] key) {
        kycKey = key;
        ed25519PublicKycKeyHex = convertByteKeyToHex(key);
    }

    public void setSupplyKey(byte[] key) {
        supplyKey = key;
        ed25519PublicSupplyKeyHex = convertByteKeyToHex(key);
    }

    public void setWipeKey(byte[] key) {
        wipeKey = key;
        ed25519PublicWipeKeyHex = convertByteKeyToHex(key);
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
}
