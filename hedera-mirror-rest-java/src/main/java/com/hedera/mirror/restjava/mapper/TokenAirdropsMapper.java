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

package com.hedera.mirror.restjava.mapper;

import com.hedera.mirror.common.domain.token.TokenAirdrop;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapperConfiguration.class)
public interface TokenAirdropsMapper {

    @Mapping(source = "receiverAccountId", target = "receiverId")
    @Mapping(source = "senderAccountId", target = "senderId")
    @Mapping(source = "serialNumber", target = "serialNumber", qualifiedByName = "mapToNullIfZero")
    @Mapping(source = "timestampRange", target = "timestamp")
    com.hedera.mirror.rest.model.TokenAirdrop map(TokenAirdrop source);

    default List<com.hedera.mirror.rest.model.TokenAirdrop> map(Collection<TokenAirdrop> source) {
        if (source == null) {
            return Collections.emptyList();
        }

        List<com.hedera.mirror.rest.model.TokenAirdrop> list = new ArrayList<>(source.size());
        for (TokenAirdrop tokenAirdrop : source) {
            list.add(map(tokenAirdrop));
        }

        return list;
    }

    @Named("mapToNullIfZero")
    default Long mapToNullIfZero(long serialNumber) {
        if (serialNumber == 0L) {
            return null;
        }
        return serialNumber;
    }
}
