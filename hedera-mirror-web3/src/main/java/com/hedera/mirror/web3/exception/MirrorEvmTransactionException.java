/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.hedera.mirror.web3.evm.exception.EvmException;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

@Getter
@SuppressWarnings("java:S110")
public class MirrorEvmTransactionException extends EvmException {

    @Serial
    private static final long serialVersionUID = 2244739157125796266L;

    private final String detail;
    private final String data;
    private final transient HederaEvmTransactionProcessingResult result;

    public MirrorEvmTransactionException(
            final ResponseCodeEnum responseCode, final String detail, final String hexData) {
        this(responseCode.name(), detail, hexData, null);
    }

    public MirrorEvmTransactionException(final String message, final String detail, final String hexData) {
        this(message, detail, hexData, null);
    }

    public MirrorEvmTransactionException(
            final String message,
            final String detail,
            final String hexData,
            HederaEvmTransactionProcessingResult result) {
        super(message);
        this.detail = detail;
        this.data = hexData;
        this.result = result;
    }

    public Bytes messageBytes() {
        final var message = getMessage();
        return Bytes.of(message.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return "%s(message=%s, detail=%s, data=%s, dataDecoded=%s)"
                .formatted(getClass().getSimpleName(), getMessage(), detail, data, decodeHex(data));
    }

    private String decodeHex(final String hex) {
        try {
            if (StringUtils.isBlank(hex)) {
                return EMPTY;
            }

            var decoded = Hex.decodeHex(hex.replace("0x", EMPTY));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return EMPTY;
        }
    }
}
