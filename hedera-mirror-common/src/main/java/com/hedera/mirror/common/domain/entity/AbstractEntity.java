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

package com.hedera.mirror.common.domain.entity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Range;
import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.converter.RangeToStringDeserializer;
import com.hedera.mirror.common.converter.RangeToStringSerializer;
import com.hedera.mirror.common.converter.UnknownIdConverter;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.common.domain.UpsertColumn;
import com.hedera.mirror.common.domain.Upsertable;
import com.hedera.mirror.common.util.DomainUtils;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Upsertable(history = true)
public abstract class AbstractEntity implements History {

    public static final long ACCOUNT_ID_CLEARED = 0L;
    public static final long NODE_ID_CLEARED = -1L;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] alias;

    private Long autoRenewAccountId;

    private Long autoRenewPeriod;

    @UpsertColumn(
            coalesce =
                    """
            case when coalesce(e_type, type) in (''ACCOUNT'', ''CONTRACT'') then coalesce(e_{0}, 0) + coalesce({0}, 0)
                 else null
            end
            """)
    private Long balance;

    @Column(updatable = false)
    private Long createdTimestamp;

    private Boolean declineReward;

    private Boolean deleted;

    private Long ethereumNonce;

    @Column(updatable = false)
    @ToString.Exclude
    private byte[] evmAddress;

    private Long expirationTimestamp;

    @Id
    private Long id;

    @ToString.Exclude
    private byte[] key;

    private Integer maxAutomaticTokenAssociations;

    private String memo;

    @Column(updatable = false)
    private Long num;

    @Convert(converter = UnknownIdConverter.class)
    private EntityId obtainerId;

    private Boolean permanentRemoval;

    @Convert(converter = AccountIdConverter.class)
    private EntityId proxyAccountId;

    private String publicKey;

    @Column(updatable = false)
    private Long realm;

    private Boolean receiverSigRequired;

    @Column(updatable = false)
    private Long shard;

    private Long stakedAccountId;

    private Long stakedNodeId;

    private Long stakePeriodStart;

    @ToString.Exclude
    private byte[] submitKey;

    @JsonDeserialize(using = RangeToStringDeserializer.class)
    @JsonSerialize(using = RangeToStringSerializer.class)
    @Type(PostgreSQLGuavaRangeType.class)
    private Range<Long> timestampRange;

    @Enumerated(EnumType.STRING)
    @Type(PostgreSQLEnumType.class)
    private EntityType type;

    public void addBalance(Long balance) {
        if (balance == null) {
            return;
        }

        if (this.balance == null) {
            this.balance = balance;
        } else {
            this.balance += balance;
        }
    }

    public void setKey(byte[] key) {
        this.key = key;
        publicKey = DomainUtils.getPublicKey(key);
    }

    public void setMemo(String memo) {
        this.memo = DomainUtils.sanitize(memo);
    }

    public EntityId toEntityId() {
        return new EntityId(shard, realm, num, type);
    }

    @SuppressWarnings("java:S1610")
    // Necessary since Lombok doesn't use our setters for builders
    public abstract static class AbstractEntityBuilder<
            C extends AbstractEntity, B extends AbstractEntityBuilder<C, B>> {
        public B key(byte[] key) {
            this.key = key;
            this.publicKey = DomainUtils.getPublicKey(key);
            return self();
        }

        public B memo(String memo) {
            this.memo = DomainUtils.sanitize(memo);
            return self();
        }
    }
}
