package com.hedera.mirror.importer.migration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("hedera.mirror.importer.blocks")
public class BlockNumberMigrationProperties {

    private boolean enabled;

    private long correctConsensusEnd;

    private long correctBlockNumber;
}
