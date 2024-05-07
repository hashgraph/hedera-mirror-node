package com.hedera.mirror.common.domain.transaction;

import lombok.Getter;
import org.apache.tuweni.bytes.Bytes;

import java.util.List;
import java.util.Map;

@Getter
public record Opcode(int pc,
                     String op,
                     long gas,
                     long gasCost,
                     int depth,
                     List<Bytes> stack,
                     List<Bytes> memory,
                     Map<String, Bytes> storage,
                     String reason) {
}
