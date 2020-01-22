package com.hedera.mirror.hcse2e.util;

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

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;

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
        var nodeId = AccountId.fromString(Dotenv.load().get("NODE_ID"));
        var nodeAddress = Dotenv.load().get("NODE_ADDRESS");

        // Build client
        var client = new Client(Map.of(nodeId, nodeAddress));

//        Client client = Client.forTestnet();
        client.setOperator(operatorId, operatorKey);

        return client;
    }
}
