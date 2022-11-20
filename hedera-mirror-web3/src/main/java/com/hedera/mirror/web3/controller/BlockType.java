package com.hedera.mirror.web3.controller;

import lombok.Data;

@Data
public class BlockType {
    static final BlockType EARLIEST = new BlockType(0L);
    static final BlockType LATEST = new BlockType(Long.MAX_VALUE);
    static final BlockType PENDING = new BlockType(Long.MAX_VALUE);

    private final long value;
}
