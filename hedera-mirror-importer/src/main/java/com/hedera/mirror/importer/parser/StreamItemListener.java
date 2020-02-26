package com.hedera.mirror.importer.parser;

import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.parser.domain.StreamItem;

public interface StreamItemListener<T extends StreamItem> {
    void onItem(T item) throws ImporterException;
}
