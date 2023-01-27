package com.hedera.mirror.graphql.mapper;

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

import static com.hedera.mirror.common.domain.entity.EntityType.UNKNOWN;
import static com.hedera.mirror.graphql.mapper.CommonMapper.CONTRACT_ID;
import static com.hedera.mirror.graphql.mapper.CommonMapper.KEYS;
import static com.hedera.mirror.graphql.mapper.CommonMapper.THRESHOLD;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.graphql.viewmodel.EntityId;
import com.hedera.mirror.graphql.viewmodel.TimestampRange;

class CommonMapperTest {

    private final CommonMapper commonMapper = Mappers.getMapper(CommonMapper.class);

    @Test
    void mapDuration() {
        var duration = Duration.ofDays(1L).minusNanos(1L).toSeconds();
        assertThat(commonMapper.mapDuration(null)).isNull();
        assertThat(commonMapper.mapDuration(0L)).isEqualTo(Duration.ZERO);
        assertThat(commonMapper.mapDuration(duration)).hasToString("PT23H59M59S");
    }

    @Test
    void mapEntityId() {
        var entityId = com.hedera.mirror.common.domain.entity.EntityId.of("1.2.3", UNKNOWN);
        assertThat(commonMapper.mapEntityId((com.hedera.mirror.common.domain.entity.EntityId) null)).isNull();
        assertThat(commonMapper.mapEntityId(entityId)).usingRecursiveComparison().isEqualTo(toEntityId(1L, 2L, 3L));
    }

    @Test
    void mapEntityIdLong() {
        assertThat(commonMapper.mapEntityId((Long) null)).isNull();
        assertThat(commonMapper.mapEntityId(0L)).usingRecursiveComparison().isEqualTo(toEntityId(0L, 0L, 0L));
    }

    @Test
    void mapKey() {
        var bytes = ByteString.copyFromUtf8("public key");
        var encoded = "cHVibGljIGtleQ==";
        var keyContractId = Key.newBuilder().setContractID(ContractID.newBuilder().setContractNum(100L)).build()
                .toByteArray();
        var keyDelegatableContractId = Key.newBuilder()
                .setDelegatableContractId(ContractID.newBuilder().setContractNum(100L))
                .build()
                .toByteArray();
        var keyEcdsaSecp25k1 = Key.newBuilder().setECDSASecp256K1(bytes).build();
        var keyEcdsa384 = Key.newBuilder().setECDSA384(bytes).build().toByteArray();
        var keyEd25519 = Key.newBuilder().setEd25519(bytes).build();
        var keyRsa3072 = Key.newBuilder().setRSA3072(bytes).build().toByteArray();
        var keyList = Key.newBuilder()
                .setKeyList(KeyList.newBuilder().addKeys(keyEcdsaSecp25k1).addKeys(keyEd25519))
                .build();
        var keyThreshold = Key.newBuilder()
                .setThresholdKey(ThresholdKey.newBuilder().setThreshold(1).setKeys(keyList.getKeyList()))
                .build()
                .toByteArray();

        assertThat(commonMapper.mapKey((byte[]) null)).isNull();
        assertThat(commonMapper.mapKey(new byte[] {})).isEqualTo(Map.of());
        assertThat(commonMapper.mapKey(new byte[] {0, 1, 2})).isNull();
        assertThat(commonMapper.mapKey(keyEcdsa384)).isEqualTo(Map.of("ECDSA_384", encoded));
        assertThat(commonMapper.mapKey(keyEcdsaSecp25k1.toByteArray())).isEqualTo(Map.of("ECDSA_SECP256K1", encoded));
        assertThat(commonMapper.mapKey(keyEd25519.toByteArray())).isEqualTo(Map.of("ED25519", encoded));
        assertThat(commonMapper.mapKey(keyRsa3072)).isEqualTo(Map.of("RSA_3072", encoded));
        assertThat(commonMapper.mapKey(keyContractId))
                .usingRecursiveComparison()
                .isEqualTo(Map.of(CONTRACT_ID, toEntityId(0L, 0L, 100L)));
        assertThat(commonMapper.mapKey(keyDelegatableContractId))
                .usingRecursiveComparison()
                .isEqualTo(Map.of("DELEGATABLE_CONTRACT_ID", toEntityId(0L, 0L, 100L)));
        assertThat(commonMapper.mapKey(keyList)).isEqualTo(Map.of(KEYS, List.of(Map.of("ECDSA_SECP256K1", encoded),
                Map.of("ED25519", encoded))));
        assertThat(commonMapper.mapKey(keyThreshold)).isEqualTo(Map.of(
                THRESHOLD, 1,
                KEYS, List.of(Map.of("ECDSA_SECP256K1", encoded), Map.of("ED25519", encoded))));
    }

    @Test
    void mapInstant() {
        var now = Instant.now();
        assertThat(commonMapper.mapInstant(null)).isNull();
        assertThat(commonMapper.mapInstant(0L)).isEqualTo(Instant.EPOCH);
        assertThat(commonMapper.mapInstant(DomainUtils.convertToNanosMax(now))).isEqualTo(now);
    }

    @Test
    void mapRange() {
        var range = new TimestampRange();
        range.setFrom(Instant.EPOCH);
        assertThat(commonMapper.mapRange(null)).isNull();
        assertThat(commonMapper.mapRange(Range.atLeast(0L)))
                .usingRecursiveComparison()
                .isEqualTo(range);

        range.setTo(Instant.now());
        assertThat(commonMapper.mapRange(Range.openClosed(0L, DomainUtils.convertToNanosMax(range.getTo()))))
                .usingRecursiveComparison()
                .isEqualTo(range);
    }

    private EntityId toEntityId(Long shard, Long realm, Long num) {
        var viewModel = new EntityId();
        viewModel.setShard(shard);
        viewModel.setRealm(realm);
        viewModel.setNum(num);
        return viewModel;
    }
}
