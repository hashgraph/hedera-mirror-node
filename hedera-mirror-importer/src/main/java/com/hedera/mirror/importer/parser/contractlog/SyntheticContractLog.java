package com.hedera.mirror.importer.parser.contractlog;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;

public interface SyntheticContractLog {
    byte[] getData();
    EntityId getEntityId();
    byte[] getTopic0();
    byte[] getTopic1();
    byte[] getTopic2();
    RecordItem getRecordItem();
}
