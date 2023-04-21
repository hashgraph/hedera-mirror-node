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
import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

@Data
@MappedSuperclass
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@TypeDef(defaultForType = Range.class, typeClass = PostgreSQLGuavaRangeType.class)
@TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
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
    private Range<Long> timestampRange;

    @Enumerated(EnumType.STRING)
    @Type(type = "pgsql_enum")
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
