/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Log4j2
@Named
@RequiredArgsConstructor
public class CustomFeeRepositoryImpl implements CustomFeeRepositoryExtra {

    public static final String SELECT_QUERY =
            "select * from custom_fee  where token_id = :tokenId and created_timestamp = (select created_timestamp from custom_fee  where token_id = :tokenId order by created_timestamp desc limit 1)";
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private static final DataClassRowMapper<CustomFee> rowMapper;

    static {
        var defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(
                Long.class, EntityId.class, AccountIdConverter.INSTANCE::convertToEntityAttribute);
        rowMapper = new DataClassRowMapper<>(CustomFee.class);
        rowMapper.setConversionService(defaultConversionService);
    }

    @Override
    public List<CustomFee> findByTokenId(Long tokenId) {
        final var parameters = new MapSqlParameterSource("tokenId", tokenId);

        return jdbcTemplate.query(SELECT_QUERY, parameters, rowMapper);
    }
}
