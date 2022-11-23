package com.hedera.mirror.web3.controller;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class BlockTypeConverter implements
        org.springframework.core.convert.converter.Converter<String, BlockType>,
        com.fasterxml.jackson.databind.util.Converter<String, BlockType> {

    private static final String EARLIEST = "earliest";
    private static final String PENDING = "pending";

    @Override
    public JavaType getInputType(TypeFactory typeFactory) {
        return typeFactory.constructType(String.class);
    }

    @Override
    public JavaType getOutputType(TypeFactory typeFactory) {
        return typeFactory.constructType(BlockType.class);
    }

    @Override
    public BlockType convert(String blockTypeString) {
        if (blockTypeString.equalsIgnoreCase(EARLIEST)) {
            return BlockType.EARLIEST;
        } else if (blockTypeString.equalsIgnoreCase(PENDING)) {
            return BlockType.PENDING;
        }
        return BlockType.LATEST;
    }
}
