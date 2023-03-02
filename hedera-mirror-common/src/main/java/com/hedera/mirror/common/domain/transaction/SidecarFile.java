package com.hedera.mirror.common.domain.transaction;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.IdClass;
import javax.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

import com.hedera.mirror.common.converter.LongListToStringSerializer;
import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.services.stream.proto.TransactionSidecarRecord;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
@Data
@Entity
@IdClass(SidecarFile.Id.class)
@NoArgsConstructor
public class SidecarFile implements Persistable<SidecarFile.Id> {

    @ToString.Exclude
    @Transient
    private byte[] actualHash;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private byte[] bytes;

    @javax.persistence.Id
    private long consensusEnd;

    private Integer count;

    @Enumerated
    private DigestAlgorithm hashAlgorithm;

    @ToString.Exclude
    private byte[] hash;

    @Column(name = "id")
    @javax.persistence.Id
    private int index;

    private String name;

    @Builder.Default
    @ToString.Exclude
    @Transient
    private List<TransactionSidecarRecord> records = Collections.emptyList();

    private Integer size;

    @Builder.Default
    @Type(type = "com.vladmihalcea.hibernate.type.array.ListArrayType")
    @JsonSerialize(using = LongListToStringSerializer.class)
    private List<Integer> types = Collections.emptyList();

    @JsonIgnore
    @Override
    public Id getId() {
        var id = new Id();
        id.setConsensusEnd(consensusEnd);
        id.setIndex(index);
        return id;
    }

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = -5844173241500874821L;

        private long consensusEnd;

        private int index;
    }
}
