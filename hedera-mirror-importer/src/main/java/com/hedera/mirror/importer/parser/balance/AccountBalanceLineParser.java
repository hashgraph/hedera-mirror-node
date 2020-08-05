package com.hedera.mirror.importer.parser.balance;

import com.google.common.base.Splitter;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Named;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Named
public class AccountBalanceLineParser {
    private static final Splitter SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();

    /**
     * Parses an account balance line to extract shard, realm, account, and balance. If the shard matches systemShardNum,
     * creates and returns an {@code AccountBalance} entity object. The account balance line should be in the
     * format of "shard,realm,account,balance"
     * @param line The account balance line
     * @param consensusTimestamp The consensus timestamp of the account balance line
     * @param systemShardNum The system shard number
     * @return {@code AccountBalance} entity object
     * @exception InvalidDatasetException if the line is malformed or the shard does not match {@code systemShardNum}
     */
    public AccountBalance parse(String line, long consensusTimestamp, long systemShardNum) {
        try {
            List<Long> parts = SPLITTER.splitToStream(line)
                    .map(Long::valueOf)
                    .filter(n -> n >= 0)
                    .collect(Collectors.toList());
            if (parts.size() != 4) {
                throw new InvalidDatasetException("Invalid account balance line: " + line);
            }
            if (parts.get(0) != systemShardNum) {
                throw new InvalidDatasetException(String.format("Invalid account balance line: %s. Expect " +
                        "shard (%d), got shard (%d)", line, systemShardNum, parts.get(0)));
            }

            long realmNum = parts.get(1);
            long accountNum = parts.get(2);
            long balance = parts.get(3);
            return new AccountBalance(balance,
                    new AccountBalance.AccountBalanceId(consensusTimestamp, (int)accountNum, (int)realmNum));
        } catch (NullPointerException | NumberFormatException ex) {
            throw new InvalidDatasetException("Invalid account balance line: " + line, ex);
        }
    }
}
