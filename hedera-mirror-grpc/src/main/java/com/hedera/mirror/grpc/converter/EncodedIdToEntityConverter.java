package com.hedera.mirror.grpc.converter;

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

import javax.inject.Named;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import com.hedera.mirror.grpc.domain.Entity;
import com.hedera.mirror.grpc.domain.EntityType;

@Named
@WritingConverter
public class EncodedIdToEntityConverter implements Converter<Long, Entity> {
    public static final EncodedIdToEntityConverter INSTANCE = new EncodedIdToEntityConverter();

    static final int REALM_BITS = 16;
    static final int NUM_BITS = 32; // bits for entity num
    private static final long REALM_MASK = (1L << REALM_BITS) - 1;
    private static final long NUM_MASK = (1L << NUM_BITS) - 1;

    @Override
    public Entity convert(Long encodedId) {
        if (encodedId == null || encodedId < 0) {
            return null;
        }

        long shard = encodedId >> (REALM_BITS + NUM_BITS);
        long realm = (encodedId >> NUM_BITS) & REALM_MASK;
        long num = encodedId & NUM_MASK;

        Entity.EntityBuilder builder = Entity.builder()
                .num(num)
                .realm(realm)
                .shard(shard)
                .id(encodedId)
                .type(EntityType.ACCOUNT);

        return builder.build();
    }
}
