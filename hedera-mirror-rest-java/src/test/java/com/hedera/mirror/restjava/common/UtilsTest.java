/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.common;

import static com.hedera.mirror.restjava.common.Constants.ACCOUNT_ID;
import static com.hedera.mirror.restjava.common.Utils.getPaginationLink;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.hedera.mirror.restjava.exception.InvalidParametersException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class UtilsTest {

    @Mock
    HttpServletRequest request;

    @ParameterizedTest
    @ValueSource(strings = {"3", "65535.000000001", "1.2.3", "0.2.3", "2814792716779530", "" + (Long.MAX_VALUE)})
    @DisplayName("EntityId isValidEntityId tests, positive cases")
    void isValidEntityIdPatternSuccess(String inputId) {
        assertTrue(Utils.isValidEntityIdPattern(inputId));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "0.1.2.3",
                "-1.0.1",
                "0.-1.1",
                "0.0.-1",
                "100000.65535.000000001",
                "100000.000000001",
                "-1",
                "" + Long.MAX_VALUE + 1
            })
    @DisplayName("EntityId isValidEntityId tests, negative cases")
    void isValidEntityIdPatternFailure(String inputId) {
        assertFalse(Utils.isValidEntityIdPattern(inputId));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "0.1.x",
                "0.1.2.3",
                "a",
                "a.b.c",
                "-1.-1.-1",
                "-1",
                "0.0.-1",
                "0.0.4294967296",
                "32768.65536.4294967296",
                "100000.65535.000000001",
                "100000.000000001",
                "0x",
                "0x00000001000000000000000200000000000000034",
                "0x2540be3f6001fffffffffffff001fffffffffffff",
                "0x10000000000000000000000000000000000000000",
                "2.3.0000000100000000000000020000000000000007",
                "9223372036854775807"
            })
    @DisplayName("EntityId parse from string tests, negative cases")
    void entityParseFromStringFailure(String inputId) {
        assertThrows(InvalidParametersException.class, () -> Utils.parseId(inputId));
    }

    @Test
    @DisplayName("EntityId parse from string tests")
    void entityParseFromString() {
        assertArrayEquals(new long[] {0, 0, 0}, Utils.parseId("0.0.0"));
        assertArrayEquals(new long[] {0, 0, 0}, Utils.parseId("0"));
        assertArrayEquals(new long[] {0, 0, 4294967295L}, Utils.parseId("0.0.4294967295"));
        assertArrayEquals(new long[] {32767, 65535, 4294967295L}, Utils.parseId("32767.65535.4294967295"));
        assertArrayEquals(new long[] {0, 0, 4294967295L}, Utils.parseId("4294967295"));
        assertArrayEquals(new long[] {0, 5, 2820130815L}, Utils.parseId("24294967295"));
        assertArrayEquals(new long[] {0, 0, 1}, Utils.parseId("0.1"));
        assertArrayEquals(new long[] {0, 0, 1}, Utils.parseId("0x0000000000000000000000000000000000000001"));
        assertArrayEquals(new long[] {0, 0, 1}, Utils.parseId("0000000000000000000000000000000000000001"));
        assertArrayEquals(new long[] {1, 2, 3}, Utils.parseId("0x0000000100000000000000020000000000000003"));
        assertArrayEquals(
                new long[] {32767, 65535, 4294967295L}, Utils.parseId("0x00007fff000000000000ffff00000000ffffffff"));
        assertArrayEquals(new long[] {0, 0, 25623323}, Utils.parseId("0.0.000000000000000000000000000000000186Fb1b"));
        assertArrayEquals(new long[] {0, 0, 25623323}, Utils.parseId("0.000000000000000000000000000000000186Fb1b"));
        assertArrayEquals(new long[] {0, 0, 25623323}, Utils.parseId("000000000000000000000000000000000186Fb1b"));
        assertArrayEquals(new long[] {0, 0, 25623323}, Utils.parseId("0x000000000000000000000000000000000186Fb1b"));
        assertArrayEquals(new long[] {1, 2, 7}, Utils.parseId("0000000100000000000000020000000000000007"));
        assertArrayEquals(new long[] {1, 2, 7}, Utils.parseId("0x0000000100000000000000020000000000000007"));
        assertArrayEquals(new long[] {1, 2, 7}, Utils.parseId("1.2.0000000100000000000000020000000000000007"));
        // Handle null and evm address cases
    }

    @Test
    @DisplayName("EntityId parse from encoded Id")
    void entityParseFromEncodedId() {
        assertArrayEquals(new long[] {0, 0, 0}, Utils.parseFromEncodedId("0"));
        assertArrayEquals(new long[] {0, 0, 4294967295L}, Utils.parseId("4294967295"));
        assertArrayEquals(new long[] {10, 10, 10}, Utils.parseId("2814792716779530"));
        assertArrayEquals(new long[] {32767, 65535, 4294967294L}, Utils.parseId("9223372036854775806"));
        assertArrayEquals(new long[] {32767, 0, 0}, Utils.parseId("9223090561878065152"));
    }

    @Test
    @DisplayName("Get pagination links")
    void getPaginationLinks() {
        var uri = "/api/v1/1001/accounts/allowances/nfts";
        when(request.getRequestURI()).thenReturn(uri);
        var lastValues = Map.of(ACCOUNT_ID, "0.0.2000");
        var included = Map.of(ACCOUNT_ID, true);
        assertEquals(
                getPaginationLink(request, false, lastValues, included, Sort.Direction.ASC, 2),
                uri + "?limit=2&order=asc&account.id=gte:0.0.2000");
    }

    @Test
    @DisplayName("Empty next link")
    void getEmptyPaginationLinks() {
        var uri = "/api/v1/1001/accounts/allowances/nfts";
        when(request.getRequestURI()).thenReturn(uri);
        var lastValues = Map.of(ACCOUNT_ID, "0.0.2000");
        var included = Map.of(ACCOUNT_ID, true);

        // When the last element has already been returned
        assertNull(getPaginationLink(request, true, lastValues, included, Sort.Direction.ASC, 2));
        // When the lastValues map is null
        assertNull(getPaginationLink(request, false, null, included, Sort.Direction.ASC, 2));
        // When the no lastValues have been passed
        assertNull(getPaginationLink(request, false, Map.of(), Map.of(), Sort.Direction.ASC, 2));
    }
}
