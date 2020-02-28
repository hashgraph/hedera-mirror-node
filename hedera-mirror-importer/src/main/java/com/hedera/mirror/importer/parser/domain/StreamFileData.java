package com.hedera.mirror.importer.parser.domain;

import lombok.Value;
import java.io.InputStream;

@Value
public class StreamFileData {
    private final String filename;
    private final InputStream inputStream;
}
