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

package com.hedera.mirror.restjava.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AllowancesControllerIntegrationTest extends RestJavaIntegrationTest {

    @LocalServerPort
    private int port;

    private String callUri;

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private NftAllowanceMapper mapper;

    private RestClient restClient;

    @BeforeEach
    void setup() {
        callUri = "http://localhost:%d/api/v1/accounts/{id}/allowances/nfts".formatted(port);
        ;
        restClient = RestClient.builder()
                .baseUrl(callUri)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Access-Control-Request-Method", "GET")
                .defaultHeader("Origin", "http://example.com")
                .build();
    }

    @Test
    void successWithNoQueryParamsShardRealmNumAccountId() {
        var allowance1 = domainBuilder.nftAllowance().persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()))
                .persist();
        Collection<NftAllowance> collection = List.of(allowance1, allowance2);

        var result = restClient
                .get()
                .uri(callUri, allowance1.getOwner())
                .accept(MediaType.ALL)
                .retrieve()
                .body(NftAllowancesResponse.class);
        assertThat(result.getAllowances()).isEqualTo(mapper.map(collection));
        assertNull(result.getLinks().getNext());
    }

    @Test
    void successWithAllQueryParamsOrderAsc() {
        // Creating nft allowances
        var allowance1 = domainBuilder.nftAllowance().persist();
        domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()))
                .persist();

        // Setting up the url params
        var uriParams = "?account.id=gte:0.0.1000&owner=true&token.id=gt:0.0.1000&limit=1&order=asc";

        var nextLink =
                "/api/v1/accounts/%s/allowances/nfts?owner=true&limit=1&order=asc&account.id=gte:%s&token.id=gt:%s"
                        .formatted(
                                allowance1.getOwner(),
                                EntityId.of(allowance1.getSpender()),
                                EntityId.of(allowance1.getTokenId()));

        // Performing the GET operation
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getOwner())
                .retrieve()
                .body(NftAllowancesResponse.class);

        assertThat(result.getAllowances()).isEqualTo(mapper.map(List.of(allowance1)));
        assertThat(result.getLinks().getNext()).isEqualTo(nextLink);
    }

    @Test
    void successWithNoOperators() {
        // Creating nft allowances
        var allowance1 = domainBuilder.nftAllowance().persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()))
                .persist();

        var uriParams = "?account.id={account.id}&limit=1&order=asc";
        var nextLink = "/api/v1/accounts/%s/allowances/nfts?limit=1&order=asc&account.id=gte:%s&token.id=gt:%s"
                .formatted(
                        allowance1.getOwner(),
                        EntityId.of(allowance2.getSpender()),
                        EntityId.of(allowance2.getTokenId()));

        // Performing the GET operation
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getOwner(), EntityId.of(allowance1.getSpender()))
                .retrieve()
                .body(NftAllowancesResponse.class);

        // This test will need to change after the new repository layer is integrated to return the correct result for
        // spenderId = allowance.spender()
        assertThat(result.getAllowances()).isEqualTo(mapper.map(List.of(allowance2)));
        assertThat(result.getLinks().getNext()).isEqualTo(nextLink);
    }

    @Test
    void successWithAllQueryParamsOrderDesc() {
        // Creating nft allowances
        var allowance1 = domainBuilder.nftAllowance().persist();
        var allowance2 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()))
                .persist();

        var uriParams = "?account.id=gte:0.0.1000&owner=true&token.id=gt:0.0.1000&limit=1&order=desc";
        var nextLink =
                "/api/v1/accounts/%s/allowances/nfts?owner=true&limit=1&order=desc&account.id=lte:%s&token.id=lt:%s"
                        .formatted(
                                allowance2.getOwner(),
                                EntityId.of(allowance2.getSpender()),
                                EntityId.of(allowance2.getTokenId()));

        // Performing the GET operation
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getOwner())
                .retrieve()
                .body(NftAllowancesResponse.class);

        assertThat(result.getAllowances()).isEqualTo(mapper.map(List.of(allowance2)));
        assertThat(result.getLinks().getNext()).isEqualTo(nextLink);
    }

    @Test
    void successWithOwnerFalse() {
        // Creating nft allowances
        var allowance1 = domainBuilder.nftAllowance().persist();
        domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance1.getOwner()))
                .persist();

        var uriParams = "?account.id=gte:0.0.1000&owner=false&token.id=gt:0.0.1000&limit=1&order=asc";
        var nextLink =
                "/api/v1/accounts/%s/allowances/nfts?owner=false&limit=1&order=asc&account.id=gte:%s&token.id=gt:%s"
                        .formatted(
                                allowance1.getSpender(),
                                EntityId.of(allowance1.getOwner()),
                                EntityId.of(allowance1.getTokenId()));

        // Performing the GET operation
        var result = restClient
                .get()
                .uri(uriParams, allowance1.getSpender())
                .retrieve()
                .body(NftAllowancesResponse.class);

        assertThat(result.getAllowances()).isEqualTo(mapper.map(List.of(allowance1)));
        assertThat(result.getLinks().getNext()).isEqualTo(nextLink);
    }

    @ParameterizedTest
    @CsvSource({
        "0.0.1001,1.2.3.4,0.0.2000,false,2,asc",
        "0.0.1001,0.0.2000,1.2.3.4,false,2,asc",
        "0.0.1001,0.0.3000,0.0.2000,false,-1,asc",
        "0.0.1001,0.0.3000,0.0.2000,false,111,asc",
        "0.0.1001,0.0.3000,0.0.2000,false,3,ttt",
        "0.0.1001,gee:0.0.3000,0.0.2000,false,3,asc",
        "0.0.4294967296,ge:0.0.3000,gte:0.0.3000,false,3,asc",
        "9223372036854775807,0.0.3000,0.0.2000,false,3,asc",
        "0x00000001000000000000000200000000000000034,0.0.3000,0.0.2000,false,3,asc"
    })
    void failWithInvalidParams(String id, String accountId, String tokenId, String owner, String limit, String order) {

        String uriParams = "?account.id={accountId}&owner={owner}&token.id={token.id}&limit={limit}&order={order}";

        // Performing the GET operation
        assertThrows(HttpClientErrorException.BadRequest.class, () -> restClient
                .get()
                .uri(uriParams, id, accountId, owner, tokenId, limit, order)
                .retrieve()
                .body(NftAllowancesResponse.class));
    }

    @Test
    void failTokenIdPresentWithoutAccount() {

        String uriParams = "?owner=false&token.id=gt:0.0.1000&limit=1&order=asc";

        // Performing the GET operation
        assertThrows(
                HttpClientErrorException.NotFound.class,
                () -> restClient.get().uri(uriParams, 1000).retrieve().body(NftAllowancesResponse.class));
    }

    @Test
    void failWithUnsupportedMediaException() {
        RestClient restClient = RestClient.create();

        // Performing the GET operation
        assertThrows(
                HttpClientErrorException.MethodNotAllowed.class,
                () -> restClient.post().uri(callUri, "0.0.1000").retrieve().body(NftAllowancesResponse.class));
    }
}
