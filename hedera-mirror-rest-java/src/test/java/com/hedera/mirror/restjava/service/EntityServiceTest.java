/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.service;

import com.google.common.io.BaseEncoding;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdAliasParameter;
import com.hedera.mirror.restjava.common.EntityIdEvmAddressParameter;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RequiredArgsConstructor
class EntityServiceTest extends RestJavaIntegrationTest {

    private final EntityService service;

    @Test
    void lookup() {
        var entity = domainBuilder.entity().persist();
        var id = entity.toEntityId();

        assertThat(service.lookup(new EntityIdNumParameter(entity.toEntityId())))
                .isEqualTo(id);
        assertThat(service.lookup(new EntityIdEvmAddressParameter(0, 0, entity.getEvmAddress())))
                .isEqualTo(id);
        assertThat(service.lookup(new EntityIdAliasParameter(0, 0, entity.getAlias())))
                .isEqualTo(id);
    }

    @Test
    @SuppressWarnings("java:S5778")
    void lookupEntityNotPresent() {

        assertThrows(
                EntityNotFoundException.class,
                () -> service.lookup(
                        getEntityIdEvmAddressParameter("000000000000000000000000000000000186Fb1b", 0L, 0L)));
        assertThrows(
                EntityNotFoundException.class, () -> service.lookup(getEntityIdAliasParameter("AABBCC22", 0L, 0L)));
    }

    @ParameterizedTest
    @CsvSource({
        "1.0.5000, null, null,1,0, NUM",
        "0.1.5000, null, null,0,1, NUM",
        "'null', AABBCC22, null,0,1, ALIAS",
        "'null', AABBCC22, null,1,1, ALIAS",
        "null, null, 000000000000000000000000000000000186Fb1b,0,1, EVMADDRESS",
        "null, null, 000000000000000000000000000000000186Fb1b,1,0, EVMADDRESS"
    })
    @SuppressWarnings("java:S5778")
    void lookupInvalidShardRealm() {

        assertThrows(IllegalArgumentException.class, () -> service.lookup(getEntityId("1.0.5000")));
        assertThrows(IllegalArgumentException.class, () -> service.lookup(getEntityId("0.1.5000")));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.lookup(
                        getEntityIdEvmAddressParameter("000000000000000000000000000000000186Fb1b", 0L, 1L)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.lookup(
                        getEntityIdEvmAddressParameter("000000000000000000000000000000000186Fb1b", 1L, 0L)));
        assertThrows(
                IllegalArgumentException.class, () -> service.lookup(getEntityIdAliasParameter("AABBCC22", 0L, 1L)));
        assertThrows(
                IllegalArgumentException.class, () -> service.lookup(getEntityIdAliasParameter("AABBCC22", 1L, 0L)));
    }

    @NotNull
    private static EntityIdAliasParameter getEntityIdAliasParameter(String id, Long shard, Long realm) {
        return new EntityIdAliasParameter(shard, realm, BaseEncoding.base32().omitPadding().decode(id));
    }

    @NotNull
    private static EntityIdEvmAddressParameter getEntityIdEvmAddressParameter(String id, Long shard, Long realm)
            throws DecoderException {
        return new EntityIdEvmAddressParameter(shard, realm, Hex.decodeHex(id));
    }

    @NotNull
    private static EntityIdNumParameter getEntityId(String id) {
        return new EntityIdNumParameter(EntityId.of(id));
    }
}
