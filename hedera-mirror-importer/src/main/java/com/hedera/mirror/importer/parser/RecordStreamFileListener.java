package com.hedera.mirror.importer.parser;

import java.util.Optional;

import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;

public interface RecordStreamFileListener extends StreamFileListener<RecordFile> {
    Optional<RecordFile> onStart(StreamFileData streamFileData) throws ImporterException;

    void onEnd(RecordFile recordFile) throws ImporterException;

    void onError();
}
