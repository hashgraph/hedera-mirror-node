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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import com.hedera.mirror.restjava.common.EntityIdType;
import com.hedera.mirror.restjava.converter.EntityIdArgumentConverter;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

import static com.hedera.mirror.restjava.common.Constants.BASE32;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RequiredArgsConstructor
public class EntityServiceTest extends RestJavaIntegrationTest {

    private final EntityService service;

    @Test
    void lookup() throws DecoderException {
        var entity = domainBuilder.entity().persist();
        var id = entity.getId();

        assertThat(service.lookup(new EntityIdParameter(entity.toEntityId(), null, null, 0L, 0L, EntityIdType.NUM)))
                .isEqualTo(id);
        assertThat(service.lookup(
                        new EntityIdParameter(null, entity.getEvmAddress(), null, 0L, 0L, EntityIdType.EVMADDRESS)))
                .isEqualTo(id);
        assertThat(service.lookup(new EntityIdParameter(null, null, entity.getAlias(), 0L, 0L, EntityIdType.ALIAS)))
                .isEqualTo(id);
    }

    @ParameterizedTest
    @CsvSource({
        "0.0.5000, null, null, NUM",
        "'null', AABBCC22, null, ALIAS",
        "null, null, 000000000000000000000000000000000186Fb1b, EVMADDRESS"
    })
    void lookupEntityNotPresent(
            @ConvertWith(EntityIdArgumentConverter.class) EntityId id,
            String alias,
            String evmAddress,
            EntityIdType type)
            throws DecoderException {

        var decodedAlias = !alias.equalsIgnoreCase("null") ? BASE32.decode(alias) : null;
        var decodedEvmAddress = !evmAddress.equalsIgnoreCase("null") ? Hex.decodeHex(evmAddress) : null;
        var entityParam = new EntityIdParameter(id, decodedEvmAddress, decodedAlias, 0L, 0L, type);

        assertThrows(EntityNotFoundException.class, () -> service.lookup(entityParam));
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
    void lookupInvalidShardRealm(
            @ConvertWith(EntityIdArgumentConverter.class) EntityId id,
            String alias,
            String evmAddress,
            Long shard,
            Long realm,
            EntityIdType type)
            throws DecoderException {

        var decodedAlias = !alias.equalsIgnoreCase("null") ? BASE32.decode(alias) : null;
        var decodedEvmAddress = !evmAddress.equalsIgnoreCase("null") ? Hex.decodeHex(evmAddress) : null;
        var entityParam = new EntityIdParameter(id, decodedEvmAddress, decodedAlias, shard, realm, type);

        assertThrows(IllegalArgumentException.class, () -> service.lookup(entityParam));
    }
}
