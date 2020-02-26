package com.hedera.mirror.importer.parser;

import com.hedera.mirror.importer.exception.ImporterException;

/**
 * As items are parsed during processing of streams, the on*() function corresponding to that item (in this
 * interface or sub-interface) will be called.
 * Invocation pattern: [...sub-interfaces calls...] [onBatchComplete | onError]
 */
public interface ParsedItemHandler {
    /**
     * Called after successful parsing of stream file.
     *
     * @throws ImporterException
     */
    void onFileComplete() throws ImporterException;

    /**
     * Called if an error is encountered during processing of stream file.
     */
    void onError(Throwable e);
}
