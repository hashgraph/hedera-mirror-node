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

package com.hedera.mirror.common.domain.balance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.StreamItem;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@NamedEntityGraph(name = "AccountBalance.tokenBalances", attributeNodes = @NamedAttributeNode("tokenBalances"))
public class AccountBalance implements Persistable<AccountBalance.Id>, StreamItem {

    private long balance;

    @Builder.Default
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    @OneToMany(
            cascade = {CascadeType.ALL},
            orphanRemoval = true)
    // set updatable = false to prevent additional hibernate query
    @JoinColumn(name = "accountId", updatable = false)
    @JoinColumn(name = "consensusTimestamp", updatable = false)
    private List<TokenBalance> tokenBalances = new ArrayList<>();

    @EmbeddedId
    @JsonUnwrapped
    private Id id;

    @JsonIgnore
    @Override
    public boolean isNew() {
        return true; // Since we never update balances and use a natural ID, avoid Hibernate querying before insert
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Embeddable
    public static class Id implements Serializable {

        private static final long serialVersionUID = 1345295043157256768L;

        private long consensusTimestamp;

        @Convert(converter = AccountIdConverter.class)
        private EntityId accountId;
    }
}
