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
import static com.hedera.mirror.restjava.common.ParameterNames.OWNER;
import static com.hedera.mirror.restjava.common.ParameterNames.TOKEN_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.hedera.mirror.rest.model.NftAllowance;
import com.hedera.mirror.restjava.common.LinkFactory.ParameterExtractor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkFactoryTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private ParameterExtractor<NftAllowance> extractor;

    private NftAllowance nftAllowance =
            new NftAllowance().owner("0.0.1000").spender("0.0.2000").tokenId("0.0.6458");

    private static final String uri = "/api";

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn(uri);
        when(extractor.extract(ACCOUNT_ID)).thenReturn(NftAllowance::getOwner);
        when(extractor.extract(OWNER)).thenReturn(NftAllowance::getOwner);
        when(extractor.extract(TOKEN_ID)).thenReturn(NftAllowance::getTokenId);
        when(extractor.isInclusive(ACCOUNT_ID)).thenReturn(true);
    }

    @DisplayName("Get pagination links for all query parameters")
    @ParameterizedTest
    @CsvSource({
        "1, ASC,  0.0.1000,    0.0.1000,    false, /api?limit=1&order=asc&account.id=gte:0.0.1000&token.id=gt:0.0.6458&owner=false",
        "1, DESC, 0.0.1000,    0.0.1000,    false, /api?limit=1&order=desc&account.id=0.0.1000&account.id=lte:0.0.1000&token.id=0.0.1000&token.id=lt:0.0.6458&owner=false",
        "1, DESC, gt:0.0.900,  gt:0.0.900,  false, /api?limit=1&order=desc&account.id=gt:0.0.900&account.id=lte:0.0.1000&token.id=gt:0.0.900&token.id=lt:0.0.6458&owner=false",
        "1, DESC, lt:0.0.9000, gte:0.0.900, false, /api?limit=1&order=desc&account.id=lt:0.0.9000&account.id=lte:0.0.1000&token.id=gte:0.0.900&token.id=lt:0.0.6458&owner=false",
        "1, DESC, lt:0.0.9000, gte:0.0.900, true,  /api?limit=1&order=desc&account.id=lt:0.0.9000&account.id=lte:0.0.2000&token.id=gte:0.0.900&token.id=lt:0.0.6458&owner=true",
        "2, ASC,  0.0.1000,    0.0.1000, false,",
        "2, DESC, 0.0.1000,    0.0.1000, false,",
    })
    void testAllQueryParameters(
            String limit,
            String order,
            String accountParameter,
            String tokenParameter,
            boolean owner,
            String expectedLink) {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("limit", new String[] {limit});
        params.put("order", new String[] {order});
        params.put("account.id", new String[] {accountParameter});
        params.put("token.id", new String[] {tokenParameter});
        params.put("owner", new String[] {Boolean.toString(owner)});
        when(request.getParameterMap()).thenReturn(params);
        when(extractor.extract(ACCOUNT_ID)).thenReturn(owner ? NftAllowance::getSpender : NftAllowance::getOwner);
        var sort = Sort.by(Direction.valueOf(order), ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, Integer.parseInt(limit), sort);
        var linkFactory = new LinkFactoryImpl(request);

        assertThat(linkFactory
                        .create(List.of(nftAllowance), pageable, extractor)
                        .getNext())
                .isEqualTo(expectedLink);
    }

    @DisplayName("Get pagination links with no primary sort")
    @ParameterizedTest
    @CsvSource({
        "0.0.1000,    0.0.1000,    /api?limit=1&account.id=gte:0.0.1000&token.id=gt:0.0.6458",
        "lt:0.0.9000, gte:0.0.900, /api?limit=1&account.id=lt:0.0.1000&token.id=gte:0.0.6458",
    })
    void testNoPrimarySort(String accountParameter, String tokenParameter, String expectedLink) {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {accountParameter});
        params.put("token.id", new String[] {tokenParameter});
        when(request.getParameterMap()).thenReturn(params);
        var pageable = PageRequest.of(0, 1);
        var linkFactory = new LinkFactoryImpl(request);

        assertThat(linkFactory
                        .create(List.of(nftAllowance), pageable, extractor)
                        .getNext())
                .isEqualTo(expectedLink);
    }

    @DisplayName("Get pagination links with secondary sort")
    @ParameterizedTest
    @CsvSource({
        "false, /api?limit=1&account.id=lt:0.0.9000&account.id=lte:0.0.1000&token.id=gte:0.0.900",
        "true,  /api?limit=1&account.id=lt:0.0.9000&account.id=lte:0.0.1000&token.id=gte:0.0.900&token.id=lt:0.0.6458",
    })
    void testSecondarySort(boolean secondarySort, String expectedLink) {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {"lt:0.0.9000"});
        params.put("token.id", new String[] {"gte:0.0.900"});
        when(request.getParameterMap()).thenReturn(params);
        var sort = secondarySort ? Sort.by(Direction.DESC, ACCOUNT_ID, TOKEN_ID) : Sort.by(Direction.DESC, ACCOUNT_ID);
        var pageable = PageRequest.of(0, 1, sort);
        var linkFactory = new LinkFactoryImpl(request);

        assertThat(linkFactory
                        .create(List.of(nftAllowance), pageable, extractor)
                        .getNext())
                .isEqualTo(expectedLink);
    }

    @DisplayName("Get pagination links with inclusive")
    @ParameterizedTest
    @CsvSource({
        "false, /api?limit=1&account.id=gt:0.0.1000&token.id=gt:0.0.6458",
        "true,  /api?limit=1&account.id=gte:0.0.1000&token.id=gt:0.0.6458",
    })
    void testInclusive(boolean inclusive, String expectedLink) {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {"0.0.1000"});
        when(request.getParameterMap()).thenReturn(params);
        when(extractor.isInclusive(ACCOUNT_ID)).thenReturn(inclusive);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);
        var linkFactory = new LinkFactoryImpl(request);

        assertThat(linkFactory
                        .create(List.of(nftAllowance), pageable, extractor)
                        .getNext())
                .isEqualTo(expectedLink);
    }

    @DisplayName("Get pagination links unknown parameter")
    @Test
    void testUnknownParameter() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {"0.0.1000"});
        params.put("unknown", new String[] {"value"});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);
        var linkFactory = new LinkFactoryImpl(request);

        assertThat(linkFactory
                        .create(List.of(nftAllowance), pageable, extractor)
                        .getNext())
                .isEqualTo("/api?limit=1&account.id=gte:0.0.1000&token.id=gt:0.0.6458");
    }

    @DisplayName("Get pagination links invalid operator")
    @Test
    void testInvalidOperator() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("limit", new String[] {"1"});
        params.put("account.id", new String[] {"gtl:0.0.1000"});
        when(request.getParameterMap()).thenReturn(params);
        var sort = Sort.by(Direction.ASC, ACCOUNT_ID, TOKEN_ID);
        var pageable = PageRequest.of(0, 1, sort);
        var linkFactory = new LinkFactoryImpl(request);
        var list = List.of(nftAllowance);
        assertThrows(IllegalArgumentException.class, () -> linkFactory.create(list, pageable, extractor));
    }

    @Test
    @DisplayName("Empty next link")
    void testEmptyPaginationLinks() {
        var linkFactory = new LinkFactoryImpl(request);
        // When the no item has been passed
        assertThat(linkFactory.create(null, null, extractor).getNext()).isNull();
        assertThat(linkFactory
                        .create(List.of(), PageRequest.ofSize(1), extractor)
                        .getNext())
                .isNull();
        assertThat(linkFactory
                        .create(List.of(nftAllowance), PageRequest.ofSize(1), extractor)
                        .getNext())
                .isNull();
        assertThat(linkFactory
                        .create(List.of(nftAllowance), PageRequest.ofSize(2), extractor)
                        .getNext())
                .isNull();
    }
}
