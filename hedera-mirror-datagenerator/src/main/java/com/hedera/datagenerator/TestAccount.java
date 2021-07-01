package com.hedera.datagenerator;/*
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;

@SpringBootApplication
public class TestAccount implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(TestAccount.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Map<String, AccountId> test = new HashMap<>();
        test.put("localhost:50211", AccountId.fromString("0.0.3"));
        Client client = Client.forNetwork(test);
        client.setOperator(AccountId.fromString("0.0.2"), PrivateKey
                .fromString(
                        "302e020100300506032b65700422042091132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137"));

        new AccountCreateTransaction()
                .setAccountMemo("")
                .setInitialBalance(Hbar.fromTinybars(1000))
                .setKey(generateKeys())
                .setMaxTransactionFee(Hbar.fromTinybars(1_000_000_000))
                .setReceiverSignatureRequired(false)
                .setTransactionMemo("")
                .execute(client);
    }

    private PublicKey generateKeys() {
        PrivateKey privateKey = PrivateKey.generate();

        return privateKey.getPublicKey();
    }
}
