/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.rest.model.TokenAirdropsResponse;
import org.junit.jupiter.api.Test;

class TokenAirdropModelTest {
    String airdropsResponse =
            """
            {
              "airdrops": [
                {
                  "amount": 333,
                  "receiver_id": "0.0.999",
                  "sender_id": "0.0.222",
                  "serial_number": null,
                  "timestamp": {
                    "from": "1111111111.111111111",
                    "to": null
                  },
                  "token_id": "0.0.111"
                },
                {
                  "amount": 555,
                  "receiver_id": "0.0.999",
                  "sender_id": "0.0.222",
                  "serial_number": null,
                  "timestamp": {
                    "from": "1111111111.111111112",
                    "to": null
                  },
                  "token_id": "0.0.444"
                },
                {
                  "amount": null,
                  "receiver_id": "0.0.999",
                  "sender_id": "0.0.222",
                  "serial_number": 888,
                  "timestamp": {
                    "from": "1111111111.111111113",
                    "to": null
                  },
                  "token_id": "0.0.666"
                }
              ],
              "links": {
                "next": "/api/v1/accounts/0.0.1000/airdrops/outstanding?limit=3&order=asc&token.id=gt:0.0.667"
              }
            }
            """;

    @Test
    void verifyModelGeneration() throws JsonProcessingException {
        var mapper = new ObjectMapper();
        var response = mapper.readValue(airdropsResponse, TokenAirdropsResponse.class);
        var tokenAirdrop = mapper.writeValueAsString(response);
        assertThat(tokenAirdrop).isEqualToIgnoringWhitespace(airdropsResponse);
    }
}
