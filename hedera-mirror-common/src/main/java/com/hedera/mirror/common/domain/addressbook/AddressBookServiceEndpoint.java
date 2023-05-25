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
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Builder(toBuilder = true)
@Data
@Entity
@IdClass(AddressBookServiceEndpoint.Id.class)
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // For builder
public class AddressBookServiceEndpoint implements Persistable<AddressBookServiceEndpoint.Id> {

    @jakarta.persistence.Id
    private long consensusTimestamp;

    @jakarta.persistence.Id
    @Column(name = "ip_address_v4")
    private String ipAddressV4;

    @jakarta.persistence.Id
    private long nodeId;

    @jakarta.persistence.Id
    private int port;

    @JsonIgnore
    @Override
    public Id getId() {
        Id id = new Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setIpAddressV4(ipAddressV4);
        id.setNodeId(nodeId);
        id.setPort(port);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    public static class Id implements Serializable {

        private static final long serialVersionUID = -7779136597707252814L;

        private long consensusTimestamp;

        @Column(name = "ip_address_v4")
        private String ipAddressV4;

        private long nodeId;

        private int port;
    }
}
