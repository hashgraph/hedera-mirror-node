/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor;

import com.hedera.hashgraph.sdk.AccountId;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@Validated
public class NodeProperties {

    @NotBlank
    private String accountId;

    @Getter(lazy = true)
    @ToString.Exclude
    private final List<AccountId> accountIds = List.of(AccountId.fromString(getAccountId()));

    @NotBlank
    private String host;

    @Min(0)
    private int port = 50211;

    public NodeProperties(String accountId, String host) {
        this.accountId = accountId;
        this.host = host;
    }

    public String getEndpoint() {
        // Allow for in-process testing of gRPC stubs
        if (host.startsWith("in-process:")) {
            return host;
        }
        return host + ":" + port;
    }
}
