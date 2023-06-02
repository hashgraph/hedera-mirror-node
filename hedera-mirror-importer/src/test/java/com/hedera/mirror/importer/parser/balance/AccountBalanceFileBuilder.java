/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.parser.balance;

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;

import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.binary.Hex;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.importer.domain.StreamFilename;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

@Named
@RequiredArgsConstructor
public class AccountBalanceFileBuilder {

    private final DomainBuilder domainBuilder;

    public Builder accountBalanceFile() {
        return accountBalanceFile(domainBuilder.timestamp());
    }

    public Builder accountBalanceFile(long consensusTimestamp) {
        return new Builder(consensusTimestamp);
    }

    public class Builder {

        private final List<AccountBalance> accountBalanceList = new ArrayList<>();
        private final long consensusTimestamp;

        private Builder(long consensusTimestamp) {
            this.consensusTimestamp = consensusTimestamp;
        }

        public Builder accountBalance(AccountBalance accountBalance) {
            accountBalance.getId().setConsensusTimestamp(consensusTimestamp);
            for (var tokenBalance : accountBalance.getTokenBalances()) {
                tokenBalance.getId().setConsensusTimestamp(consensusTimestamp);
            }
            accountBalanceList.add(accountBalance);
            return this;
        }

        public AccountBalanceFile build() {
            Assert.notEmpty(accountBalanceList, "Must contain at least one account balance");

            Instant instant = Instant.ofEpochSecond(0, consensusTimestamp);
            String filename = StreamFilename.getFilename(StreamType.BALANCE, DATA, instant);
            return AccountBalanceFile.builder()
                    .bytes(domainBuilder.bytes(16))
                    .count((long) accountBalanceList.size())
                    .consensusTimestamp(consensusTimestamp)
                    .fileHash(Hex.encodeHexString(domainBuilder.bytes(48)))
                    .items(Flux.fromIterable(accountBalanceList))
                    .loadStart(domainBuilder.timestamp())
                    .name(filename)
                    .nodeId(0L)
                    .build();
        }
    }
}
