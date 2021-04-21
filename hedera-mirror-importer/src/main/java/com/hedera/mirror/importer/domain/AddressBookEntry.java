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

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.codec.binary.Hex;

import com.hedera.mirror.importer.converter.AccountIdConverter;

@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"publicKey", "nodeCertHash"})
public class AddressBookEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long consensusTimestamp;

    private String description;

    private String memo;

    private String ip;

    private Integer port;

    private String publicKey;

    private Long stake;

    private Long nodeId;

    @Convert(converter = AccountIdConverter.class)
    private EntityId nodeAccountId;

    private byte[] nodeCertHash;

    public PublicKey getPublicKeyAsObject() {
        try {
            byte[] bytes = Hex.decodeHex(publicKey);
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public EntityId getNodeAccountId() {
        if (EntityId.isEmpty(nodeAccountId)) {
            return memo == null ? null : EntityId.of(memo, EntityTypeEnum.ACCOUNT);
        }

        return nodeAccountId;
    }

    @Transient
    public String getNodeAccountIdString() {
        return EntityId.isEmpty(nodeAccountId) ? memo : nodeAccountId.entityIdToString();
    }
}
