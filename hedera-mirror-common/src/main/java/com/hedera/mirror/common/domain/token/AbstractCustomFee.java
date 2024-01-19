/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import com.hedera.mirror.common.converter.ObjectToStringSerializer;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.UpsertColumn;
import com.hedera.mirror.common.domain.Upsertable;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.CollectionUtils;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractCustomFee implements History {

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @UpsertColumn(shouldCoalesce = false)
    private List<FixedFee> fixedFees;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @UpsertColumn(shouldCoalesce = false)
    private List<FractionalFee> fractionalFees;

    @JsonSerialize(using = ObjectToStringSerializer.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @UpsertColumn(shouldCoalesce = false)
    private List<RoyaltyFee> royaltyFees;

    private Range<Long> timestampRange;

    @Id
    private Long tokenId;

    public void addFixedFee(@NonNull FixedFee fixedFee) {
        if (this.fixedFees == null) {
            this.fixedFees = new ArrayList<>();
        }

        this.fixedFees.add(fixedFee);
    }

    public void addFractionalFee(@NonNull FractionalFee fractionalFee) {
        if (this.fractionalFees == null) {
            this.fractionalFees = new ArrayList<>();
        }

        this.fractionalFees.add(fractionalFee);
    }

    public void addRoyaltyFee(@NonNull RoyaltyFee royaltyFee) {
        if (this.royaltyFees == null) {
            this.royaltyFees = new ArrayList<>();
        }

        this.royaltyFees.add(royaltyFee);
    }

    @JsonIgnore
    public boolean isEmptyFee() {
        return CollectionUtils.isEmpty(this.fixedFees)
                && CollectionUtils.isEmpty(this.fractionalFees)
                && CollectionUtils.isEmpty(this.royaltyFees);
    }
}
