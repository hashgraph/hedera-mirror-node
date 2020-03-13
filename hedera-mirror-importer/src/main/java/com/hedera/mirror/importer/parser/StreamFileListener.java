package com.hedera.mirror.importer.parser;

import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;

public interface StreamFileListener<T> {
    /**
     * Called when starting to process a new stream file.
     *
     * @return true if the file processing should continue; false to skip the file.
     * @throws ImporterException
     */
    boolean onStart(StreamFileData streamFileData) throws ImporterException;

    void onEnd(T fileInfo) throws ImporterException;

    /**
     * Called if an error is encountered during processing of stream file.
     */
    void onError();
}
