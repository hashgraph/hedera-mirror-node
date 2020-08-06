package com.hedera.mirror.importer.parser.balance;

import javax.inject.Named;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

@Named
public class AccountBalanceLineParser {

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
            String[] parts = line.split(",");
            if (parts.length != 4) {
                throw new InvalidDatasetException("Invalid account balance line: " + line);
            }

            long shardNum = Long.parseLong(parts[0]);
            int realmNum = Integer.parseInt(parts[1]);
            int accountNum = Integer.parseInt(parts[2]);
            long balance = Long.parseLong(parts[3]);
            if (shardNum < 0 || realmNum < 0 || accountNum < 0 || balance < 0) {
                throw new InvalidDatasetException("Invalid account balance line: " + line);
            }

            if (shardNum != systemShardNum) {
                throw new InvalidDatasetException(String.format("Invalid account balance line: %s. Expect " +
                        "shard (%d), got shard (%d)", line, systemShardNum, shardNum));
            }

            return new AccountBalance(balance,
                    new AccountBalance.AccountBalanceId(consensusTimestamp, accountNum, realmNum));
        } catch (NullPointerException | NumberFormatException ex) {
            throw new InvalidDatasetException("Invalid account balance line: " + line, ex);
        }
    }
}
