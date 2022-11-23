package com.hedera.mirror.web3.controller.convert;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import com.hedera.mirror.web3.controller.BlockType;

import java.io.IOException;

public class BlockTypeSerializer extends JsonSerializer<BlockType> {

    @Override
    public void serialize(BlockType value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.getName());
    }
}
