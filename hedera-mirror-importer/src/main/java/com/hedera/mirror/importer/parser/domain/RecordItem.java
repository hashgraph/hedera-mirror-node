package com.hedera.mirror.importer.parser.domain;

import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import lombok.Value;

@Value
public class RecordItem implements StreamItem {
    private final Transaction transaction;
    private final TransactionRecord record;
    private final byte[] transactionBytes;
    private final byte[] recordBytes;
}
