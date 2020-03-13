package com.hedera.mirror.importer.parser;

import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;

public interface RecordStreamFileListener extends StreamFileListener<RecordFile> {
}
