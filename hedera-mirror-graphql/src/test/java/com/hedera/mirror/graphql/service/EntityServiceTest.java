package com.hedera.mirror.graphql.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.graphql.repository.EntityRepository;

@ExtendWith(MockitoExtension.class)
class EntityServiceTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final Base32 base32 = new Base32();
    private final int EVM_ADDRESS_BYTE_LENGTH = 20;

    @Mock
    private EntityRepository entityRepository;

    @InjectMocks
    private EntityServiceImpl entityService;

    @Test
    void getByIdAndTypeMissing() {
        var entity = domainBuilder.entity().get();
        assertThat(entityService.getByIdAndType(entity.toEntityId(), entity.getType())).isEmpty();
    }

    @Test
    void getByAliasAndTypeMissing() {
        var entity = domainBuilder.entity().get();
        assertThat(entityService.getByAliasAndType(base32.encodeAsString(entity.getAlias()), entity.getType())).isEmpty();
    }

    @Test
    void getByEvmAddressAndTypeMissing() {
        var entity = domainBuilder.entity().get();
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(entity.getEvmAddress()), entity.getType()))
                .isEmpty();
    }

    @Test
    void getByIdAsEvmAddressAndTypeMissing() {
        var entity = domainBuilder.entity().get();
        ByteBuffer evmBuffer = ByteBuffer.allocate(EVM_ADDRESS_BYTE_LENGTH);
        evmBuffer.putLong(EVM_ADDRESS_BYTE_LENGTH - Long.BYTES, entity.getId());
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(evmBuffer), entity.getType()))
                .isEmpty();
    }

    @Test
    void getByIdAndTypeMismatch() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByIdAndType(entity.toEntityId(), EntityType.CONTRACT)).isEmpty();
    }

    @Test
    void getByAliasAndTypeMismatch() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findByAlias(entity.getAlias())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByAliasAndType(base32.encodeAsString(entity.getAlias()), EntityType.CONTRACT)).isEmpty();
    }

    @Test
    void getByEvmAddressAndTypeMismatch() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findByEvmAddress(entity.getEvmAddress())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(entity.getEvmAddress()),
                EntityType.CONTRACT)).isEmpty();
    }

    @Test
    void getByIdAsEvmAddressAndTypeMismatch() {

        var entity = domainBuilder.entity().get();
        ByteBuffer evmBuffer = ByteBuffer.allocate(EVM_ADDRESS_BYTE_LENGTH);
        evmBuffer.putLong(EVM_ADDRESS_BYTE_LENGTH - Long.BYTES, entity.getId());
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(evmBuffer),
                EntityType.CONTRACT)).isEmpty();
    }

    @Test
    void getByIdAndTypeFound() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByIdAndType(entity.toEntityId(), entity.getType())).get().isEqualTo(entity);
    }

    @Test
    void getByAliasAndTypeFound() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findByAlias(entity.getAlias())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByAliasAndType(base32.encodeAsString(entity.getAlias()), entity.getType())).get()
                .isEqualTo(entity);
    }

    @Test
    void getByEvmAddressAndTypeFound() {
        var entity = domainBuilder.entity().get();
        when(entityRepository.findByEvmAddress(entity.getEvmAddress())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(entity.getEvmAddress()),
                entity.getType())).get()
                .isEqualTo(entity);
    }

    @Test
    void getByEvmAddressLookAlikeEntityAndTypeFound() {
        var entity = domainBuilder.entity().get();
        int integerStringLength = Integer.BYTES * 2;
        String padZero = StringUtils.repeat('0', integerStringLength);
        String evmAddress = padZero + Hex.encodeHexString(entity.getEvmAddress()).substring(integerStringLength);
        try {
            when(entityRepository.findByEvmAddress(Hex.decodeHex(evmAddress))).thenReturn(Optional.of(entity));
        } catch (DecoderException e) {
            throw new RuntimeException(e);
        }
        assertThat(entityService.getByEvmAddressAndType(evmAddress,
                entity.getType())).get()
                .isEqualTo(entity);
    }

    @Test
    void getByIdAsEvmAddressAndTypeFound() {
        var entity = domainBuilder.entity().get();
        ByteBuffer evmBuffer = ByteBuffer.allocate(EVM_ADDRESS_BYTE_LENGTH);
        evmBuffer.putLong(EVM_ADDRESS_BYTE_LENGTH - Long.BYTES, entity.getId());
        when(entityRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThat(entityService.getByEvmAddressAndType(Hex.encodeHexString(evmBuffer),
                entity.getType())).get()
                .isEqualTo(entity);
    }
}
