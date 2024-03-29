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
import static com.hedera.mirror.restjava.common.Constants.TOKEN_ID;
import static com.hedera.mirror.restjava.common.Utils.getPaginationLink;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class UtilsTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private ServletRequestAttributes attributes;

    private MockedStatic<RequestContextHolder> context;

    @BeforeEach
    void setUp() {
        context = Mockito.mockStatic(RequestContextHolder.class);
    }

    @AfterEach
    void closeMocks() {
        context.close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "0.1.x",
                "x.1",
                "0.1.2.3",
                "a",
                "a.b.c",
                "-1.-1.-1",
                "-1",
                "0.0.-1",
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
        assertThrows(IllegalArgumentException.class, () -> Utils.parseId(inputId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.4294967296", "32768.65536.4294967296"})
    @DisplayName("EntityId parse from string tests, negative cases for ID having valid format")
    void testInvalidEntity(String input) {
        assertThrows(InvalidEntityException.class, () -> Utils.parseId(input));
    }

    @Test
    @DisplayName("EntityId parse from string tests")
    void entityParseFromString() {
        assertThat(EntityId.of(0, 0, 0)).isEqualTo(Utils.parseId("0.0.0"));
        assertThat(EntityId.of(0, 0, 0)).isEqualTo(Utils.parseId("0"));
        assertThat(EntityId.of(0, 0, 4294967295L)).isEqualTo(Utils.parseId("0.0.4294967295"));
        assertThat(EntityId.of(0, 65535, 1)).isEqualTo(Utils.parseId("65535.000000001"));
        assertThat(EntityId.of(32767, 65535, 4294967295L)).isEqualTo(Utils.parseId("32767.65535.4294967295"));
        assertThat(EntityId.of(0, 0, 4294967295L)).isEqualTo(Utils.parseId("4294967295"));
        assertThat(EntityId.of(0, 0, 1)).isEqualTo(Utils.parseId("0.1"));
    }

    @Test
    @DisplayName("Get pagination links")
    void getPaginationLinks() {
        var uri = "/api/v1/1001/accounts/allowances/nfts";
        context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
        when(attributes.getRequest()).thenReturn(request);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getParameterMap()).thenReturn(Map.of());
        assertThat(getPaginationLink(false, getLastValues(), Sort.Direction.ASC))
                .isEqualTo(uri + "?account.id=gte:0.0.2000&token.id=gt:0.0.6458");
    }

    @Test
    @DisplayName("Get pagination links for all query parameters")
    void getPaginationLinksAllQueryParameters() {
        var uri = "/api/v1/1001/accounts/allowances/nfts";
        Map<String, String[]> params = getParamMap();

        context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
        when(attributes.getRequest()).thenReturn(request);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getParameterMap()).thenReturn(params);
        assertThat(getPaginationLink(false, getLastValues(), Sort.Direction.ASC))
                .isEqualTo(uri + "?limit=2&order=asc&account.id=gte:0.0.2000&token.id=gt:0.0.6458&owner=false");
    }

    @Test
    @DisplayName("Get pagination links for all query parameters, order descending")
    void getPaginationLinksAllQueryParametersOrderDesc() {
        var uri = "/api/v1/1001/accounts/allowances/nfts";
        Map<String, String[]> params = getParamMap();
        params.put("order", new String[] {"desc"});
        context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
        when(attributes.getRequest()).thenReturn(request);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getParameterMap()).thenReturn(params);
        assertThat(getPaginationLink(false, getLastValues(), Sort.Direction.DESC))
                .isEqualTo(uri + "?limit=2&order=desc&account.id=lte:0.0.2000&token.id=lt:0.0.6458&owner=false");
    }

    @Test
    @DisplayName("Empty next link")
    void getEmptyPaginationLinks() {
        LinkedHashMap<String, String> lastValues = new LinkedHashMap<>(Map.of(ACCOUNT_ID, "0.0.2000"));
        // When the last element has already been returned
        assertNull(getPaginationLink(true, lastValues, Sort.Direction.ASC));
        // When the lastValues map is null
        assertNull(getPaginationLink(false, null, Sort.Direction.ASC));
        // When the no lastValues have been passed
        assertNull(getPaginationLink(false, new LinkedHashMap<>(Map.of()), Sort.Direction.ASC));
    }

    @Test
    @DisplayName("No request object available")
    void nullRequestObject() {
        context.when(RequestContextHolder::getRequestAttributes).thenReturn(null);
        assertNull(getPaginationLink(false, getLastValues(), Sort.Direction.ASC));
    }

    @NotNull
    private static Map<String, String[]> getParamMap() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("limit", new String[] {"2"});
        params.put("order", new String[] {"asc"});
        params.put("account.id", new String[] {"0.0.1000"});
        params.put("token.id", new String[] {"0.0.1000"});
        params.put("owner", new String[] {"false"});
        return params;
    }

    @NotNull
    private static LinkedHashMap<String, String> getLastValues() {
        LinkedHashMap<String, String> lastValues = new LinkedHashMap<>();
        lastValues.put(ACCOUNT_ID, "0.0.2000");
        lastValues.put(TOKEN_ID, "0.0.6458");
        return lastValues;
    }
}
