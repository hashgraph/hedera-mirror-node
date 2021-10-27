package com.hedera.mirror.importer.converter;

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.importer.domain.EntityTypeEnum;

@ExtendWith(MockitoExtension.class)
class EntityTypeSerializerTest {

    @Mock
    JsonGenerator jsonGenerator;

    @Test
    void testNull() throws Exception {
        // when
        new EntityTypeSerializer().serialize(null, jsonGenerator, null);

        // then
        verify(jsonGenerator).writeNull();
    }

    @Test
    void testEntityType() throws Exception {
        // when
        new EntityTypeSerializer().serialize(EntityTypeEnum.TOKEN, jsonGenerator, null);

        // then
        verify(jsonGenerator).writeNull();
    }
}
