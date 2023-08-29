/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import java.io.Serial;
import lombok.Value;
import org.springframework.context.ApplicationEvent;

@Value
public class AccountBalanceFileParsedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 6739609173639179005L;

    private final long consensusTimestamp;

    public AccountBalanceFileParsedEvent(Object source, long consensusTimestamp) {
        super(source);
        this.consensusTimestamp = consensusTimestamp;
    }
}
