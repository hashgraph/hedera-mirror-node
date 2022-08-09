package com.hedera.services.transaction;

import com.esaulpaugh.headlong.abi.TupleType;

public class ParsingConstants {
    public static final String INT32 = "(int32)";

    public static final TupleType notSpecifiedType = TupleType.parse(INT32);

    public enum FunctionType {
        ERC_NAME,
        ERC_SYMBOL
    }

}
