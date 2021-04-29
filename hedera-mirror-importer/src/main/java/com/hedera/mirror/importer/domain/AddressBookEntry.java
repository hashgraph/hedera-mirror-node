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
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.codec.binary.Hex;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.importer.converter.AccountIdConverter;

@Builder(toBuilder = true)
@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"publicKey", "nodeCertHash"})
public class AddressBookEntry implements Persistable<AddressBookEntry.Id>, Serializable {
    private static final long serialVersionUID = -2037596800253225229L;

    @JsonIgnore
    @EmbeddedId
    @JsonUnwrapped
    private AddressBookEntry.Id id;

    private String description;

    private String memo;

    private String publicKey;

    @Convert(converter = AccountIdConverter.class)
    private EntityId nodeAccountId;

    private byte[] nodeCertHash;

    @EqualsAndHashCode.Exclude
    @JoinColumn(name = "consensusTimestamp", referencedColumnName = "consensusTimestamp")
    @JoinColumn(name = "nodeId", referencedColumnName = "nodeId")
    @JsonIgnore
    @OneToMany(cascade = {CascadeType.ALL}, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AddressBookServiceEndpoint> serviceEndpoints = new ArrayList<>();

    private Long stake;

    public PublicKey getPublicKeyAsObject() {
        try {
            byte[] bytes = Hex.decodeHex(publicKey);
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            var keyFactory = KeyFactory.getInstance("RSA");
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

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id implements Serializable {

        private static final long serialVersionUID = -3761184325551298389L;

        private Long consensusTimestamp;

        private Long nodeId;
    }
}
