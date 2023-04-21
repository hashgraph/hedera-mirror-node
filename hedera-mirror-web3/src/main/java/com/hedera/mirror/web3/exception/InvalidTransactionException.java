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

package com.hedera.mirror.web3.exception;

import com.hedera.mirror.web3.evm.exception.EvmException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;

@SuppressWarnings("java:S110")
public class InvalidTransactionException extends EvmException {

    @Serial
    private static final long serialVersionUID = 2244739157125796266L;

    private final String detail;
    private final String data;

    public InvalidTransactionException(final ResponseCodeEnum responseCode, final String detail, final String hexData) {
        super(responseCode.name());
        this.detail = detail;
        this.data = hexData;
    }

    public InvalidTransactionException(final String message, final String detail, final String hexData) {
        super(message);
        this.detail = detail;
        this.data = hexData;
    }

    public Bytes messageBytes() {
        final var message = getMessage();
        return Bytes.of(message.getBytes(StandardCharsets.UTF_8));
    }

    public String getDetail() {
        return detail;
    }

    public String getData() {
        return data;
    }
}
