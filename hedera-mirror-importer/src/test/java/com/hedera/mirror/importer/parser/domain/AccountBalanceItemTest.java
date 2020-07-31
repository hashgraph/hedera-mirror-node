package com.hedera.mirror.importer.parser.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

import java.time.Instant;

class AccountBalanceItemTest {
    private static final long timestamp = 1596340377922333444L;

    @DisplayName("Construct AccountBalanceItem from string")
    @ParameterizedTest(name = "from \"{0}\"")
    @CsvSource(value = {
            "'0,0,123,700';false;0.0.123;700",
            "' 0,0,123,700';false;0.0.123;700",
            "'0,0,123,700 ';false;0.0.123;700",
            "' 0,0,123,700 ';false;0.0.123;700",
            "'x,0,123,700';true;;",
            "'0,x,123,700';true;;",
            "'0,0,x,700';true;;",
            "'0,0,123,a00';true;;",
            "'1000000000000000000000000000,0,123,700';true;;",
            "'0,1000000000000000000000000000,123,700';true;;",
            "'0,0,1000000000000000000000000000,700';true;;",
            "'0,0,123,1000000000000000000000000000';true;;",
            "'-1,0,123,700';true;;",
            "'0,-1,123,700';true;;",
            "'0,0,-1,700';true;;",
            "'0,0,123,-1';true;;",
            "'foobar';true;;",
            "'';true;;",
            ";true;;"
    }, delimiter = ';')
    void testOf(String line, boolean expectThrown, String accountIdStr, Long balance) {
        if (!expectThrown) {
            AccountBalanceItem accountBalanceItem = AccountBalanceItem.of(line, timestamp);
            EntityId accountId = EntityId.of(accountIdStr, EntityTypeEnum.ACCOUNT);
            assertThat(accountBalanceItem.getAccountId()).isEqualTo(accountId);
            assertThat(accountBalanceItem.getBalance()).isEqualTo(balance);
            assertThat(accountBalanceItem.getConsensusTimestamp()).isEqualTo(timestamp);
        } else {
            assertThrows(IllegalArgumentException.class, () -> {
                AccountBalanceItem.of(line, timestamp);
            });
        }
    }

	@Test
	void testToString() {
        AccountBalanceItem accountBalanceItem = new AccountBalanceItem(EntityId.of(0, 0, 163, EntityTypeEnum.ACCOUNT),  960, timestamp);
        assertThat(accountBalanceItem.toString()).isEqualTo("0.0.163=960,"
                + Instant.ofEpochSecond(timestamp / 1_000_000_000L, timestamp % 1_000_000_000L));
	}
}
