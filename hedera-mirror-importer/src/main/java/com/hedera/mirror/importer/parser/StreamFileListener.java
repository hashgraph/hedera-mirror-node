package com.hedera.mirror.importer.parser;

import java.util.Optional;

import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;

public interface StreamFileListener<T> {
    /**
     * Called when starting to process a new stream file.
     *
     * @return non-empty <T> if the file processing should continue; empty to skip the file.
     * @throws ImporterException
     */
    Optional<T> onStart(StreamFileData streamFileData) throws ImporterException;

    void onEnd(T recordFile) throws ImporterException;

    /**
     * Called if an error is encountered during processing of stream file.
     */
    void onError();
}
