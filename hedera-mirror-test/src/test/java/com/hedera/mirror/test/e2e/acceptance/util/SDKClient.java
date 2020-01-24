package com.hedera.mirror.test.e2e.acceptance.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import io.github.cdimascio.dotenv.Dotenv;
import java.util.Map;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;

@Log4j2
public class SDKClient {

    private final Client client;

    public SDKClient(String operatorid, String operatorkey) {
        var operatorId = AccountId.fromString(operatorid);
        var operatorKey = Ed25519PrivateKey.fromString(operatorkey);

        client = Client.forTestnet();
        client.setOperator(operatorId, operatorKey);
    }

    public static Client hederaClient() throws HederaStatusException {

        // Grab configuration variables from the .env file
        var operatorId = AccountId.fromString(Dotenv.load().get("OPERATOR_ID"));
        var operatorKey = Ed25519PrivateKey.fromString(Dotenv.load().get("OPERATOR_KEY"));

        Client client;
        Boolean useTestNet = Boolean.parseBoolean(Dotenv.load().get("USE_TESTNET"));
        if (useTestNet) {
            log.debug("Creating SDK client for TestNet");
            client = Client.forTestnet();
        } else {
            var nodeId = AccountId.fromString(Dotenv.load().get("NODE_ID"));
            var nodeAddress = Dotenv.load().get("NODE_ADDRESS");
            log.debug("Creating SDK client for node {} at {}", nodeId, nodeAddress);

            // Build client
            client = new Client(Map.of(nodeId, nodeAddress));
        }

        client.setOperator(operatorId, operatorKey);

        return client;
    }
}
