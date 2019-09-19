package com.hedera.parser;

import org.apache.commons.codec.binary.Hex;

public interface FileParser {

    String EMPTY_HASH = Hex.encodeHexString(new byte[48]);

    void parse();

}
