package com.hedera.mirror.importer.parser.balance.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.TokenBalances;
import com.hederahashgraph.api.proto.java.TokenID;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.InvalidDatasetException;

class AccountBalanceLineParserV2Test {

    private static final long timestamp = 1596340377922333444L;
    private static final long systemShardNum = 0;

    @DisplayName("Parse account balance line")
    @ParameterizedTest(name = "from \"{0}\"")
    @CsvSource(value = {
            "'0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ';false;" +
                    "0;123;700;CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgoDGPQHEMCopQQ",
            "'0,0,123,700,';false;" +
                    "0;123;700;;",
            "'0,0,123,700,CggKAxjsBxCEBwoHCgMY7QcQGQoKCgMY7gcQwKilBAoICgMY8gcQhAcKBwoDGPMHEBkKCgo';true;;;;",
            "' 0,0,123,700,';true;;;;",
            "'0, 0,123,700,';true;;;;",
            "'0,0, 123,700,';true;;;;",
            "'0,0,123, 700,';true;;;;",
            "'0,0,123,700,, ';true;;;;",
            "'1,0,123,700,';true;;;;",
            "'x,0,123,700,';true;;;;",
            "'0,x,123,700,';true;;;;",
            "'0,0,x,700,';true;;;;",
            "'0,0,123,a00,';true;;;;",
            "'1000000000000000000000000000,0,123,700,';true;;;;",
            "'0,1000000000000000000000000000,123,700,';true;;;;",
            "'0,0,1000000000000000000000000000,700,';true;;;;",
            "'0,0,123,1000000000000000000000000000,';true;;;;",
            "'-1,0,123,700,';true;;;;",
            "'0,-1,123,700,';true;;;;",
            "'0,0,-1,700,';true;;;;",
            "'0,0,123,-1,';true;;;;",
            "'foobar';true;;;;",
            "'';true;;;;",
            ";true;;;;"
    }, delimiter = ';')
    void parse(String line, boolean expectThrow, Long expectedRealm, Long expectedAccount, Long expectedBalance,
               String expectedTokenBalanceListString) throws IOException {
        AccountBalanceLineParserV2 parser = new AccountBalanceLineParserV2();
        if (!expectThrow) {
            List<com.hederahashgraph.api.proto.java.TokenBalance> expectedTokenBalanceList = StringUtils
                    .isNotBlank(expectedTokenBalanceListString) ?
                    TokenBalances.parseFrom(Base64.decodeBase64(expectedTokenBalanceListString.getBytes()))
                            .getTokenBalancesList() :
                    Collections.emptyList();

            AccountBalance accountBalance = parser.parse(line, timestamp, systemShardNum);
            var id = accountBalance.getId();

            assertThat(accountBalance.getBalance()).isEqualTo(expectedBalance);
            assertThat(id.getAccountId().getRealmNum()).isEqualTo(expectedRealm);
            assertThat(id.getAccountId().getEntityNum()).isEqualTo(expectedAccount);
            assertThat(id.getConsensusTimestamp()).isEqualTo(timestamp);

            List<TokenBalance> actualTokenBalanceList = accountBalance.getTokenBalances();
            assertThat(actualTokenBalanceList.size()).isEqualTo(expectedTokenBalanceList.size());
            for (int i = 0; i < expectedTokenBalanceList.size(); i++) {
                TokenBalance actualTokenBalance = actualTokenBalanceList.get(i);
                com.hederahashgraph.api.proto.java.TokenBalance expectedTokenBalance =
                        expectedTokenBalanceList.get(i);
                TokenBalance.Id actualId = actualTokenBalance.getId();
                TokenID expectedId = expectedTokenBalance.getTokenId();

                assertThat(actualTokenBalance.getBalance()).isEqualTo(expectedTokenBalance.getBalance());
                assertThat(actualId.getConsensusTimestamp()).isEqualTo(timestamp);
                assertThat(actualId.getAccountId().getRealmNum()).isEqualTo(expectedRealm);
                assertThat(actualId.getAccountId().getEntityNum()).isEqualTo(expectedAccount);

                assertThat(actualId.getTokenId().getShardNum()).isEqualTo(expectedId.getShardNum());
                assertThat(actualId.getTokenId().getRealmNum()).isEqualTo(expectedId.getRealmNum());
                assertThat(actualId.getTokenId().getEntityNum()).isEqualTo(expectedId.getTokenNum());
                assertThat(actualId.getTokenId().getType()).isEqualTo(EntityTypeEnum.TOKEN.getId());
            }
        } else {
            assertThrows(InvalidDatasetException.class, () -> {
                parser.parse(line, timestamp, systemShardNum);
            });
        }
    }
}
