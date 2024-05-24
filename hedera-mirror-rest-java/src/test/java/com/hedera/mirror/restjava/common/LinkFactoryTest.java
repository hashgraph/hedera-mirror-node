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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.mirror.rest.model.Links;
import com.hedera.mirror.rest.model.NftAllowance;
import com.hedera.mirror.rest.model.Transaction;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkFactoryTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private ServletRequestAttributes attributes;

    private MockedStatic<RequestContextHolder> context;

    @Mock
    private Function<NftAllowance, Map<String, String>> extractor;

    private final LinkFactory linkFactory = new LinkFactoryImpl();

    private final NftAllowance nftAllowance =
            new NftAllowance().owner("0.0.1000").spender("0.0.2000").tokenId("0.0.6458");

    private static final String uri = "/api";

    @BeforeEach
    void setUp() {
        context = Mockito.mockStatic(RequestContextHolder.class);
        context.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);
        when(attributes.getRequest()).thenReturn(request);
        when(request.getRequestURI()).thenReturn(uri);
        when(extractor.apply(nftAllowance))
                .thenReturn(Map.of(ACCOUNT_ID, nftAllowance.getOwner(), TOKEN_ID, nftAllowance.getTokenId()));
    }

    @AfterEach
    void closeMocks() {
        context.close();
    }

    @DisplayName("Get pagination links for all query parameters")
    @ParameterizedTest
    @CsvSource({
        "1, ASC,  eq:0.0.1000, eq:0.0.1000, false, /api?limit=1&order=ASC&account.id=eq:0.0.1000&account.id=gte:0.0.1000&token.id=eq:0.0.1000&token.id=gt:0.0.6458&owner=false",
        "1, asc,  lt:0.0.2000, 0.0.1000,    false, /api?limit=1&order=asc&account.id=lt:0.0.2000&account.id=gte:0.0.1000&token.id=0.0.1000&token.id=gt:0.0.6458&owner=false",
        "1, DESC, 0.0.1000,    0.0.1000,    false, /api?limit=1&order=DESC&account.id=0.0.1000&account.id=lte:0.0.1000&token.id=0.0.1000&token.id=lt:0.0.6458&owner=false",
        "1, desc, gt:0.0.900,  gt:0.0.900,  false, /api?limit=1&order=desc&account.id=gt:0.0.900&account.id=lte:0.0.1000&token.id=gt:0.0.900&token.id=lt:0.0.6458&owner=false",
        "1, desc, gt:0.0.900, gte:0.0.900, false,  /api?limit=1&order=desc&account.id=gt:0.0.900&account.id=lte:0.0.1000&token.id=gte:0.0.900&token.id=lt:0.0.6458&owner=false",
        "1, desc, lt:0.0.9000, gte:0.0.900, false, /api?limit=1&order=desc&token.id=gte:0.0.900&token.id=lt:0.0.6458&owner=false&account.id=lte:0.0.1000",
        "1, desc, lt:0.0.9000, gte:0.0.900, true,  /api?limit=1&order=desc&token.id=gte:0.0.900&token.id=lt:0.0.6458&owner=true&account.id=lte:0.0.2000",
        "2, ASC,  0.0.1000,    0.0.1000, false,",
        "2, desc, 0.0.1000,    0.0.1000, false,",
    })
    void testAllQueryParameters(
            String limit,
            String order,
            String accountParameter,
            String tokenParameter,
            boolean owner,
            String expectedLink) {
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {limit});
        params.put("order", new String[] {order});
        params.put("account.id", new String[] {accountParameter});
        params.put("token.id", new String[] {tokenParameter});
        params.put("owner", new String[] {Boolean.toString(owner)});
        when(request.getParameterMap()).thenReturn(params);
        when(extractor.apply(nftAllowance))
                .thenReturn(Map.of(
                        ACCOUNT_ID,
                        owner ? nftAllowance.getSpender() : nftAllowance.getOwner(),
                        TOKEN_ID,
                        nftAllowance.getTokenId()));
        var sort = Sort.by(Direction.valueOf(order.toUpperCase()), ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, Integer.parseInt(limit), sort);

        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);
    }

    @DisplayName("Get pagination links with no primary sort")
    @ParameterizedTest
    @CsvSource({
        "0.0.1000,    0.0.1000,    /api?limit=1&account.id=0.0.1000&account.id=gte:0.0.1000&token.id=0.0.1000&token.id=gt:0.0.6458",
        "lt:0.0.9000, gte:0.0.900, /api?limit=1&account.id=lt:0.0.9000&account.id=gte:0.0.1000&token.id=gt:0.0.6458",
    })
    void testNoPrimarySort(String accountParameter, String tokenParameter, String expectedLink) {
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {accountParameter});
        params.put("token.id", new String[] {tokenParameter});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);
    }

    @Test
    void testSortOmitted() {
        // given
        var transaction = new Transaction().consensusTimestamp("123456789.000000123");

        // when then
        assertThat(linkFactory.create(
                        List.of(transaction),
                        PageRequest.ofSize(1),
                        (t) -> Map.of("timestamp", t.getConsensusTimestamp())))
                .returns("/api?timestamp=gt:123456789.000000123", Links::getNext);
    }

    @DisplayName("Get pagination links unknown parameter")
    @Test
    void testUnknownParameter() {
        var params = new LinkedHashMap<String, String[]>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {"0.0.1000"});
        params.put("unknown", new String[] {"value", "value2"});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(
                        "/api?limit=1&account.id=0.0.1000&account.id=gte:0.0.1000&unknown=value&unknown=value2&token.id=gt:0.0.6458",
                        Links::getNext);
    }

    @DisplayName("Get pagination links with multiple parameter values")
    @ParameterizedTest
    @CsvSource({
        "asc,   lt:0.0.2000, lte:0.0.2000, lte:0.0.7000, gt:0.0.6000,  /api?order=asc&account.id=lt:0.0.2000&account.id=lte:0.0.2000&account.id=gte:0.0.1000&token.id=lte:0.0.7000&token.id=gt:0.0.6458",
        "desc,  lt:0.0.2000, lte:0.0.2000, lte:0.0.7000, gt:0.0.6000,  /api?order=desc&token.id=gt:0.0.6000&token.id=lt:0.0.6458&account.id=lte:0.0.1000",
        "asc,    gt:0.0.100,  gte:0.0.100, lte:0.0.7000, gt:0.0.6000,  /api?order=asc&token.id=lte:0.0.7000&token.id=gt:0.0.6458&account.id=gte:0.0.1000",
        "desc,   gt:0.0.100,  gte:0.0.100, lte:0.0.7000, gt:0.0.6000,  /api?order=desc&account.id=gt:0.0.100&account.id=gte:0.0.100&account.id=lte:0.0.1000&token.id=gt:0.0.6000&token.id=lt:0.0.6458",
        "asc,   lt:0.0.2000,   gt:0.0.100,  gt:0.0.6000, lt:0.0.7000,  /api?order=asc&account.id=lt:0.0.2000&account.id=gte:0.0.1000&token.id=lt:0.0.7000&token.id=gt:0.0.6458",
        "desc,  lt:0.0.2000,   gt:0.0.100,  gt:0.0.6000, lt:0.0.7000,  /api?order=desc&account.id=gt:0.0.100&account.id=lte:0.0.1000&token.id=gt:0.0.6000&token.id=lt:0.0.6458",
        "asc,      0.0.1000,     0.0.1000,     0.0.1000,    0.0.1000,  /api?order=asc&account.id=0.0.1000&account.id=0.0.1000&account.id=gte:0.0.1000&token.id=0.0.1000&token.id=0.0.1000&token.id=gt:0.0.6458",
        "desc,     0.0.1000,     0.0.1000,     0.0.1000,    0.0.1000,  /api?order=desc&account.id=0.0.1000&account.id=0.0.1000&account.id=lte:0.0.1000&token.id=0.0.1000&token.id=0.0.1000&token.id=lt:0.0.6458",
        "asc,      0.0.1000,  gt:0.0.1000, lte:0.0.7000, gt:0.0.6000,  /api?order=asc&account.id=0.0.1000&account.id=gte:0.0.1000&token.id=lte:0.0.7000&token.id=gt:0.0.6458",
        "desc,     0.0.1000,  gt:0.0.1000, lte:0.0.7000, gt:0.0.6000,  /api?order=desc&account.id=0.0.1000&account.id=gt:0.0.1000&account.id=lte:0.0.1000&token.id=gt:0.0.6000&token.id=lt:0.0.6458",
    })
    void testMultipleParameters(
            String order,
            String accountParameter,
            String accountParameter2,
            String tokenParameter,
            String tokenParameter2,
            String expectedLink) {
        var params = new LinkedHashMap<String, String[]>();
        params.put("order", new String[] {order});
        params.put("account.id", new String[] {accountParameter, accountParameter2});
        params.put("token.id", new String[] {tokenParameter, tokenParameter2});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.valueOf(order.toUpperCase()), ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);

        assertThat(linkFactory.create(List.of(nftAllowance), pageable, extractor))
                .returns(expectedLink, Links::getNext);
    }

    @Test
    void testNullRequestAttributes() {
        // given
        var params = new LinkedHashMap<String, String[]>();
        params.put("account.id", new String[] {"0.0.1002"});
        params.put("token.id", new String[] {"0.0.3000"});
        when(request.getParameterMap()).thenReturn(params);
        context.when(RequestContextHolder::getRequestAttributes).thenReturn(null);

        // when then
        assertThat(linkFactory.create(List.of(nftAllowance), PageRequest.ofSize(1), extractor))
                .returns(null, Links::getNext);
    }

    @Test
    @DisplayName("Empty next link")
    void testEmptyPaginationLinks() {
        assertThat(linkFactory.create(null, PageRequest.ofSize(1), extractor)).returns(null, Links::getNext);
        assertThat(linkFactory.create(List.of(), PageRequest.ofSize(1), extractor))
                .returns(null, Links::getNext);
    }
}
