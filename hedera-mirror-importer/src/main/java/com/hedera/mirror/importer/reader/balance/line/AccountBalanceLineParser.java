package com.hedera.mirror.importer.reader.balance.line;

import com.hedera.mirror.importer.domain.AccountBalance;

public interface AccountBalanceLineParser {
    AccountBalance parse(String line, long consensusTimestamp, long systemShardNum);
}
