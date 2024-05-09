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

import static com.hedera.mirror.restjava.controller.AllowancesController.extractor;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.mirror.rest.model.NftAllowance;
import com.hedera.mirror.restjava.controller.AllowancesLinkFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkFactoryTest {

    @Mock
    private HttpServletRequest request;

    private NftAllowance nftAllowance =
            new NftAllowance().owner("0.0.1000").spender("0.0.2000").tokenId("0.0.6458");

    private static final String uri = "/api/v1/1001/accounts/allowances/nfts";

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn(uri);
    }

    @Test
    @DisplayName("Get pagination links")
    void getPaginationLinks() {
        when(request.getParameterMap()).thenReturn(Map.of());
        var linkFactory = new AllowancesLinkFactory(request);

        assertThat(linkFactory.create(nftAllowance, extractor).getNext())
                .isEqualTo(uri + "?account.id=gte:0.0.2000&token.id=gt:0.0.6458");
    }

    @Test
    @DisplayName("Get pagination links for all query parameters")
    void getPaginationLinksAllQueryParameters() {
        var uri = "/api/v1/1001/accounts/allowances/nfts";
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getParameterMap()).thenReturn(getParamMap());
        var linkFactory = new AllowancesLinkFactory(request);

        assertThat(linkFactory.create(nftAllowance, extractor).getNext())
                .isEqualTo(uri + "?limit=2&order=asc&account.id=gte:0.0.1000&token.id=gt:0.0.6458&owner=false");
    }

    @Test
    @DisplayName("Get pagination links for all query parameters, order descending")
    void getPaginationLinksAllQueryParametersOrderDesc() {
        var uri = "/api/v1/1001/accounts/allowances/nfts";
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("limit", new String[] {"2"});
        params.put("order", new String[] {"desc"});
        params.put("account.id", new String[] {"gte:0.0.1000"});
        params.put("token.id", new String[] {"gt:0.0.1000"});
        params.put("owner", new String[] {"false"});
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getParameterMap()).thenReturn(params);
        var linkFactory = new AllowancesLinkFactory(request);

        assertThat(linkFactory.create(nftAllowance, extractor).getNext())
                .isEqualTo(
                        uri
                                + "?limit=2&order=desc&account.id=gte:0.0.1000&account.id=lte:0.0.2000&token.id=gt:0.0.1000&token.id=lt:0.0.6458&owner=false");
    }

    @Test
    @DisplayName("Empty next link")
    void getEmptyPaginationLinks() {
        var linkFactory = new AllowancesLinkFactory(request);
        // When the no item has been passed
        assertThat(linkFactory.create(null, extractor).getNext()).isNull();
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
}
