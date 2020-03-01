package com.hedera.mirror.importer.parser.record;

import lombok.Data;
import javax.inject.Named;

@Data
@Named
public class PostgresWriterProperties {
    /**
     * PreparedStatement.executeBatch() is called after every batchSize number of transactions from record stream file.
     */
    private int batchSize = 100;
}
