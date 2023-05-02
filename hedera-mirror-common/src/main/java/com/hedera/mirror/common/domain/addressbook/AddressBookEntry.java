/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.common.domain.addressbook;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.NonParsableKeyException;
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.codec.binary.Hex;
import org.springframework.data.domain.Persistable;

@Builder(toBuilder = true)
@Data
@Entity
@IdClass(AddressBookEntry.Id.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
public class AddressBookEntry implements Persistable<AddressBookEntry.Id> {

    @javax.persistence.Id
    private long consensusTimestamp;

    private String description;

    private String memo;

    @javax.persistence.Id
    private long nodeId;

    @Convert(converter = AccountIdConverter.class)
    private EntityId nodeAccountId;

    @ToString.Exclude
    private byte[] nodeCertHash;

    @ToString.Exclude
    private String publicKey;

    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    @ToString.Exclude
    @Transient
    private final PublicKey publicKeyObject = parsePublicKey();

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @JoinColumn(name = "consensusTimestamp", referencedColumnName = "consensusTimestamp")
    @JoinColumn(name = "nodeId", referencedColumnName = "nodeId")
    @JsonIgnore
    @OneToMany(
            cascade = {CascadeType.ALL},
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    private Set<AddressBookServiceEndpoint> serviceEndpoints = new HashSet<>();

    private Long stake;

    private PublicKey parsePublicKey() {
        try {
            byte[] bytes = Hex.decodeHex(publicKey);
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            var keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            throw new NonParsableKeyException(e);
        }
    }

    @JsonIgnore
    @Override
    public AddressBookEntry.Id getId() {
        AddressBookEntry.Id id = new AddressBookEntry.Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setNodeId(nodeId);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    public static class Id implements Serializable {

        private static final long serialVersionUID = -3761184325551298389L;

        private Long consensusTimestamp;

        private Long nodeId;
    }
}
