package com.hedera.mirror.importer.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

import com.hedera.mirror.importer.domain.EntityId;

public class EntityIdSerializer extends StdSerializer<EntityId> {

    private static final long serialVersionUID = 5158286630945397464L;

    public EntityIdSerializer() {
        super(EntityId.class);
    }

    @Override
    public void serialize(EntityId value, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
        if (value != null) {
            jsonGenerator.writeNumber(value.getId());
        }
    }
}
