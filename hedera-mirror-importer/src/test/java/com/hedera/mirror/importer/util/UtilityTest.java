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

package com.hedera.mirror.importer.util;

import static com.hedera.mirror.importer.util.Utility.HALT_ON_ERROR_PROPERTY;
import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.primitives.Bytes;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.exception.ParserException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SuppressWarnings("java:S5786")
@ExtendWith(OutputCaptureExtension.class)
public class UtilityTest {

    public static final byte[] ALIAS_ECDSA_SECP256K1 =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    public static final byte[] EVM_ADDRESS = Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b");

    @Test
    void aliasToEvmAddress() {
        byte[] aliasEd25519 = Key.newBuilder()
                .setEd25519(ByteString.copyFromUtf8("ab"))
                .build()
                .toByteArray();
        byte[] aliasEcdsa2 = Hex.decode("3a2102ff806fecbd31b4c377293cba8d2b78725965a4990e0ff1b1b29a1d2c61402310");
        byte[] aliasInvalidEcdsa = Key.newBuilder()
                .setECDSASecp256K1(ByteString.copyFrom(TestUtils.generateRandomByteArray(33)))
                .build()
                .toByteArray();
        byte[] aliasUncompressedEcdsa = Hex.decode(
                "3a374349514637575a4f55475132415336334d504757573633484750484b584b5442344f3643505a334b3437374133354b584257525a4e4941");
        byte[] evmAddress2 = Hex.decode("efa0d905af20199aa03aca71cfa5f7647f29f439");
        byte[] randomBytes = TestUtils.generateRandomByteArray(DomainUtils.EVM_ADDRESS_LENGTH);
        byte[] tooShortBytes = TestUtils.generateRandomByteArray(32);
        byte[] invalidBytes = Bytes.concat(new byte[] {'a'}, TestUtils.generateRandomByteArray(32));

        assertThat(Utility.aliasToEvmAddress(ALIAS_ECDSA_SECP256K1)).isEqualTo(EVM_ADDRESS);
        assertThat(Utility.aliasToEvmAddress(aliasEcdsa2)).isEqualTo(evmAddress2);
        assertThat(Utility.aliasToEvmAddress(randomBytes)).isEqualTo(randomBytes);
        assertThat(Utility.aliasToEvmAddress(aliasInvalidEcdsa)).isNull();
        assertThat(Utility.aliasToEvmAddress(aliasUncompressedEcdsa)).isNull();
        assertThat(Utility.aliasToEvmAddress(aliasEd25519)).isNull();
        assertThat(Utility.aliasToEvmAddress(null)).isNull();
        assertThat(Utility.aliasToEvmAddress(new byte[] {})).isNull();
        assertThat(Utility.aliasToEvmAddress(tooShortBytes)).isNull();
        assertThat(Utility.aliasToEvmAddress(invalidBytes)).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {"0,0", "86400000000000,1", "1653487416000000000,19137"})
    void getEpochDay(long timestamp, long expected) {
        assertThat(Utility.getEpochDay(timestamp)).isEqualTo(expected);
    }

    @Test
    void getTopic() {
        ContractLoginfo contractLoginfo = ContractLoginfo.newBuilder()
                .addTopic(ByteString.copyFrom(new byte[] {0, 0, 0, 0, 0, 0, 1}))
                .addTopic(ByteString.copyFrom(new byte[] {0, 127}))
                .addTopic(ByteString.copyFrom(new byte[] {-1}))
                .addTopic(ByteString.copyFrom(new byte[] {0}))
                .addTopic(ByteString.copyFrom(new byte[] {0, 0, 0, 0}))
                .addTopic(ByteString.copyFrom(new byte[0]))
                .build();
        assertThat(Utility.getTopic(contractLoginfo, 0)).isEqualTo(new byte[] {1});
        assertThat(Utility.getTopic(contractLoginfo, 1)).isEqualTo(new byte[] {127});
        assertThat(Utility.getTopic(contractLoginfo, 2)).isEqualTo(new byte[] {-1});
        assertThat(Utility.getTopic(contractLoginfo, 3)).isEqualTo(new byte[] {0});
        assertThat(Utility.getTopic(contractLoginfo, 4)).isEqualTo(new byte[] {0});
        assertThat(Utility.getTopic(contractLoginfo, 5)).isEmpty();
        assertThat(Utility.getTopic(contractLoginfo, 999)).isNull();
    }

    @Test
    @DisplayName("get TransactionId")
    void getTransactionID() {
        AccountID payerAccountId = AccountID.newBuilder()
                .setShardNum(0)
                .setRealmNum(0)
                .setAccountNum(2)
                .build();
        TransactionID transactionId = Utility.getTransactionId(payerAccountId);
        assertThat(transactionId).isNotEqualTo(TransactionID.getDefaultInstance());

        AccountID testAccountId = transactionId.getAccountID();
        assertAll(
                // row counts
                () -> assertEquals(payerAccountId.getShardNum(), testAccountId.getShardNum()),
                () -> assertEquals(payerAccountId.getRealmNum(), testAccountId.getRealmNum()),
                () -> assertEquals(payerAccountId.getAccountNum(), testAccountId.getAccountNum()));
    }

    @ParameterizedTest(name = "with seconds {0} and nanos {1}")
    @CsvSource({"1569936354, 901", "0, 901", "1569936354, 0", "0,0"})
    void instantToTimestamp(long seconds, int nanos) {
        Instant instant = Instant.ofEpochSecond(seconds).plusNanos(nanos);
        Timestamp test = Utility.instantToTimestamp(instant);
        assertAll(
                () -> assertEquals(instant.getEpochSecond(), test.getSeconds()),
                () -> assertEquals(instant.getNano(), test.getNanos()));
    }

    @ParameterizedTest(name = "Convert {0} to snake case")
    @CsvSource({",", "\"\",\"\"", "Foo,foo", "FooBar,foo_bar", "foo_bar,foo_bar"})
    void toSnakeCase(String input, String output) {
        assertThat(Utility.toSnakeCase(input)).isEqualTo(output);
    }

    @ParameterizedTest(name = "Log {0} with cause {2}")
    @CsvSource(
            textBlock =
                    """
                                plain message, plain message, false,
                                {} arg message, one arg message, true, one
                                {} {} message, a b message, false, 'a, b'
                                {} {} {} {}, a b c d, true, 'a, b, c, d'
                            """)
    void handleRecoverableErrorLogOrThrow(
            String format,
            String formatted,
            boolean addCause,
            @ConvertWith(StringArrayConverter.class) String[] args,
            CapturedOutput capturedOutput) {

        var causeMessage = "provided cause";
        var cause = new RuntimeException(causeMessage);

        Object[] logArgs;
        if (addCause) {
            List<Object> arrList = new ArrayList<>(Arrays.asList(args));
            arrList.add(cause);
            logArgs = arrList.toArray();
        } else {
            logArgs = args;
        }

        /*
         * With halt on error not set, verify expected log output is generated.
         */
        System.setProperty(HALT_ON_ERROR_PROPERTY, "false");
        if (logArgs == null) {
            Utility.handleRecoverableError(format);
        } else {
            Utility.handleRecoverableError(format, logArgs);
        }

        var allOutput = capturedOutput.getAll();
        assertThat(allOutput).contains(RECOVERABLE_ERROR + formatted);
        if (addCause) {
            assertThat(allOutput).contains(cause.getClass().getName() + ": " + causeMessage);
        }

        /*
         * With halt on error set, ensure ParserException is thrown with expected information.
         */
        System.setProperty(HALT_ON_ERROR_PROPERTY, "true");
        ParserException parserException = assertThrows(ParserException.class, () -> {
            if (logArgs == null) {
                Utility.handleRecoverableError(format);
            } else {
                Utility.handleRecoverableError(format, logArgs);
            }
        });
        assertThat(parserException.getMessage()).isEqualTo(formatted);
        if (addCause) {
            assertThat(parserException.getCause()).isSameAs(cause);
        }
    }

    public static class StringArrayConverter extends SimpleArgumentConverter {

        @Override
        protected Object convert(Object source, Class<?> targetType) throws ArgumentConversionException {
            if (source == null) {
                return null;
            }

            if (source instanceof String && String[].class.isAssignableFrom(targetType)) {
                return ((String) source).split("\\s*,\\s*");
            } else {
                throw new ArgumentConversionException(
                        "Conversion from " + source.getClass() + " to " + targetType + " not supported.");
            }
        }
    }
}
