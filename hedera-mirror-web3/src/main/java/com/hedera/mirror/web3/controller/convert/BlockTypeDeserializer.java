package com.hedera.mirror.web3.controller.convert;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import com.hedera.mirror.web3.controller.BlockType;

import java.io.IOException;

public class BlockTypeDeserializer extends JsonDeserializer<BlockType> {

    @Override
    public BlockType deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
        String blockTypeString = jsonParser.readValueAs(String.class);

        if (blockTypeString.equalsIgnoreCase("earliest")) {
            return BlockType.EARLIEST;
        } else if (blockTypeString.equalsIgnoreCase("pending")) {
            return BlockType.PENDING;
        }
        return BlockType.LATEST;
    }
}
