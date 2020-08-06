package com.hedera.mirror.importer.parser.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

class AccountBalanceLineParserTest {
    private static final long timestamp = 1596340377922333444L;
    private static final long systemShardNum = 0;

    @DisplayName("Parse account balance line")
    @ParameterizedTest(name = "from \"{0}\"")
    @CsvSource(value = {
            "'0,0,123,700';false;0;123;700",
            "' 0,0,123,700';true;;;",
            "'0, 0,123,700';true;;;",
            "'0,0, 123,700';true;;;",
            "'0,0,123, 700';true;;;",
            "'0,0,123,700 ';true;;;",
            "'1,0,123,700';true;;;",
            "'x,0,123,700';true;;;",
            "'0,x,123,700';true;;;",
            "'0,0,x,700';true;;;",
            "'0,0,123,a00';true;;;",
            "'1000000000000000000000000000,0,123,700';true;;;",
            "'0,1000000000000000000000000000,123,700';true;;;",
            "'0,0,1000000000000000000000000000,700';true;;;",
            "'0,0,123,1000000000000000000000000000';true;;;",
            "'-1,0,123,700';true;;;",
            "'0,-1,123,700';true;;;",
            "'0,0,-1,700';true;;;",
            "'0,0,123,-1';true;;;",
            "'foobar';true;;;",
            "'';true;;;",
            ";true;;;"
    }, delimiter = ';')
    void parse(String line, boolean expectThrow, Integer expectedRealm, Integer expectedAccount, Long expectedBalance) {
        AccountBalanceLineParser parser = new AccountBalanceLineParser();
        if (!expectThrow) {
            AccountBalance accountBalance = parser.parse(line, timestamp, systemShardNum);
            var id = accountBalance.getId();

            assertThat(accountBalance.getBalance()).isEqualTo(expectedBalance);
            assertThat(id.getAccountRealmNum()).isEqualTo(expectedRealm);
            assertThat(id.getAccountNum()).isEqualTo(expectedAccount);
            assertThat(id.getConsensusTimestamp()).isEqualTo(timestamp);
        } else {
            assertThrows(InvalidDatasetException.class, () -> {
                parser.parse(line, timestamp, systemShardNum);
            });
        }
    }
}
