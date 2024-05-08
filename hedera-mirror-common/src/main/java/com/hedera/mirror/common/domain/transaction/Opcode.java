package com.hedera.mirror.common.domain.transaction;

import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

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
