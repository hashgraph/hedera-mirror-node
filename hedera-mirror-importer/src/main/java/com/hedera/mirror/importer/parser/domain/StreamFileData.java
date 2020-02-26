package com.hedera.mirror.importer.parser.domain;

import lombok.Value;
import java.io.InputStream;

@Value
public class StreamFileData {
    String filename;
    InputStream inputStream;
}
