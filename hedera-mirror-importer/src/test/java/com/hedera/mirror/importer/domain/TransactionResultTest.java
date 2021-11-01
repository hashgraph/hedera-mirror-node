package com.hedera.mirror.importer.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

class TransactionResultTest {

    @Test
    void testEnums() {
//        assertArrayEquals(ResponseCodeEnum.values(), TransactionResult.values());

        for (ResponseCodeEnum protoResponse : ResponseCodeEnum.values()) {
            assertNotNull(TransactionResult.valueOf(protoResponse.name()));
            assertNotNull(TransactionResult.fromId(protoResponse.getNumber()));
        }
    }
}
