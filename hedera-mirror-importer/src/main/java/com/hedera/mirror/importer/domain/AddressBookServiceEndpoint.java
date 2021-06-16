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
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.importer.converter.AccountIdConverter;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class AddressBookServiceEndpoint implements Persistable<AddressBookServiceEndpoint.Id>, Serializable {

    private static final long serialVersionUID = 6964963511683419945L;

    public AddressBookServiceEndpoint(long consensusTimestamp, String ip, int port, EntityId nodeAccountId) {
        id = new AddressBookServiceEndpoint.Id(consensusTimestamp, ip, nodeAccountId, port);
    }

    @JsonIgnore
    @EmbeddedId
    @JsonUnwrapped
    private Id id;

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

        private static final long serialVersionUID = -7779136597707252814L;

        private long consensusTimestamp;

        @Column(name = "ip_address_v4")
        private String ipAddressV4;

        @Convert(converter = AccountIdConverter.class)
        private EntityId nodeId;

        private int port;
    }
}
