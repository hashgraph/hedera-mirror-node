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

import static com.hedera.mirror.restjava.common.ParameterNames.ACCOUNT_ID;
import static com.hedera.mirror.restjava.common.ParameterNames.TOKEN_ID;
import static com.hedera.mirror.restjava.common.Utils.getPaginationLink;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
