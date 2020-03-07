package com.hedera.mirror.importer.parser.record;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import javax.validation.constraints.Min;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.parser.record.postgresql")
public class PostgresWriterProperties {
    /**
     * PreparedStatement.executeBatch() is called after every batchSize number of transactions from record stream file.
     */
    @Min(1)
    private int batchSize = 100;
}
