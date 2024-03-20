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

import static com.hedera.mirror.restjava.common.Constants.LIMIT;
import static com.hedera.mirror.restjava.common.Constants.ORDER;
import static com.hedera.mirror.restjava.common.Constants.OWNER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.restjava.RestJavaIntegrationTest;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.assertj.core.util.Hexadecimals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class AllowancesControllerIntegrationTest extends RestJavaIntegrationTest {

    private static final String CALL_URI = "http://localhost:8094/api/v1/accounts/{id}/allowances/nfts";

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private NftAllowanceMapper mapper;

    private static final String ACCOUNT_ID = "account.id";
    private static final String TOKEN_ID = "token.id";
    private static final String accountIdParam = "account.id={account.id}&";
    private static final String ownerParam = "owner={owner}&";
    private static final String tokenIdParam = "token.id={token.id}&";
    private static final String limitParam = "limit={limit}&";
    private static final String orderParam = "order={order}";

    @Test
    void successWithNoQueryParamsShardRealmNumAccountId() {
        var allowance = domainBuilder.nftAllowance().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance.getOwner()))
                .persist();
        Collection<NftAllowance> collection = List.of(allowance, allowance1);

        RestClient restClient = RestClient.create();
        var result = restClient
                .get()
                .uri(CALL_URI, allowance.getOwner())
                .accept(MediaType.ALL)
                .retrieve()
                .body(NftAllowancesResponse.class);
        assertEquals(result.getAllowances(), (mapper.map(collection)));
        assertNull(result.getLinks().getNext());
    }

    @Test
    void successWithNoQueryParamsEncodedAccountId() {
        var allowance = domainBuilder.nftAllowance().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance.getOwner()))
                .persist();
        Collection<NftAllowance> collection = List.of(allowance, allowance1);

        RestClient restClient = RestClient.create();
        var result = restClient
                .get()
                .uri(CALL_URI, EntityId.of(allowance.getOwner()))
                .accept(MediaType.ALL)
                .retrieve()
                .body(NftAllowancesResponse.class);
        assertEquals(result.getAllowances(), (mapper.map(collection)));
        assertNull(result.getLinks().getNext());
    }

    @Test
    void successWithNoQueryParamsEvmAddress() {
        var allowance = domainBuilder.nftAllowance().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance.getOwner()))
                .persist();
        Collection<NftAllowance> collection = List.of(allowance, allowance1);

        RestClient restClient = RestClient.create();
        var result = restClient
                .get()
                .uri(CALL_URI, Hexadecimals.toHexString(DomainUtils.toEvmAddress(EntityId.of(allowance.getOwner()))))
                .accept(MediaType.ALL)
                .retrieve()
                .body(NftAllowancesResponse.class);
        assertEquals(result.getAllowances(), (mapper.map(collection)));
        assertNull(result.getLinks().getNext());
    }

    @Test
    void successWithAllQueryParamsOrderAsc() {
        // Creating nft allowances
        var allowance = domainBuilder.nftAllowance().persist();
        domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance.getOwner()))
                .persist();

        // Setting up the url params
        Map<String, String> uriVariables = Map.of(
                "id",
                "" + allowance.getOwner(),
                ACCOUNT_ID,
                "gte:1000",
                OWNER,
                "true",
                TOKEN_ID,
                "gt:1000",
                LIMIT,
                "1",
                ORDER,
                "ASC");

        // Creating the rest client with the uri variables
        RestClient restClient = RestClient.builder()
                .baseUrl(CALL_URI)
                .defaultUriVariables(uriVariables)
                .build();

        // Performing the GET operation
        var result = restClient
                .get()
                .uri("?" + accountIdParam + ownerParam + tokenIdParam + limitParam + orderParam)
                .retrieve()
                .body(NftAllowancesResponse.class);

        assertEquals(result.getAllowances(), mapper.map(List.of(allowance)));
        assertEquals(
                result.getLinks().getNext(),
                "/api/v1/accounts/" + allowance.getOwner()
                        + "/allowances/nfts?limit=1&order=asc&account.id=gte:" + EntityId.of(allowance.getSpender())
                        + "&token.id=gt:" + EntityId.of(allowance.getTokenId()));
    }

    @Test
    void successWithNoOperators() {
        // Creating nft allowances
        var allowance = domainBuilder.nftAllowance().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance.getOwner()))
                .persist();

        // Setting up the url params
        Map<String, String> uriVariables = Map.of(
                "id",
                String.valueOf(allowance.getOwner()),
                ACCOUNT_ID,
                String.valueOf(allowance.getSpender()),
                OWNER,
                "true",
                LIMIT,
                "1",
                ORDER,
                "ASC");

        // Creating the rest client with the uri variables
        RestClient restClient = RestClient.builder()
                .baseUrl(CALL_URI)
                .defaultUriVariables(uriVariables)
                .build();

        // Performing the GET operation
        var result = restClient
                .get()
                .uri("?" + accountIdParam + ownerParam + limitParam + orderParam)
                .retrieve()
                .body(NftAllowancesResponse.class);

        // This test will need to change after the new repository layer is integrated to return the correct result for
        // spenderId = allowance.spender()
        assertEquals(result.getAllowances(), mapper.map(List.of(allowance1)));
        assertEquals(
                result.getLinks().getNext(),
                "/api/v1/accounts/" + allowance.getOwner()
                        + "/allowances/nfts?limit=1&order=asc&account.id=gte:" + EntityId.of(allowance1.getSpender())
                        + "&token.id=gt:" + EntityId.of(allowance1.getTokenId()));
    }

    @Test
    void successWithAllQueryParamsOrderDesc() {
        // Creating nft allowances
        var allowance = domainBuilder.nftAllowance().persist();
        var allowance1 = domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance.getOwner()))
                .persist();

        // Setting up the url params
        Map<String, String> uriVariables = Map.of(
                "id",
                "" + allowance.getOwner(),
                ACCOUNT_ID,
                "gte:1000",
                OWNER,
                "true",
                TOKEN_ID,
                "gt:1000",
                LIMIT,
                "1",
                ORDER,
                "DESC");
        var params = "?" + accountIdParam + ownerParam + tokenIdParam + limitParam + orderParam;

        // Creating the rest client with the uri variables
        RestClient restClient = RestClient.builder()
                .baseUrl(CALL_URI)
                .defaultUriVariables(uriVariables)
                .build();

        // Performing the GET operation
        var result = restClient.get().uri(params).retrieve().body(NftAllowancesResponse.class);

        assertEquals(result.getAllowances(), mapper.map(List.of(allowance1)));
        assertEquals(
                result.getLinks().getNext(),
                "/api/v1/accounts/" + allowance1.getOwner()
                        + "/allowances/nfts?limit=1&order=desc&account.id=lte:" + EntityId.of(allowance1.getSpender())
                        + "&token.id=lt:" + EntityId.of(allowance1.getTokenId()));
    }

    @Test
    void successWithOwnerFalse() {
        // Creating nft allowances
        var allowance = domainBuilder.nftAllowance().persist();
        domainBuilder
                .nftAllowance()
                .customize(nfta -> nfta.owner(allowance.getOwner()))
                .persist();

        // Setting up the url params
        Map<String, String> uriVariables = Map.of(
                "id",
                "" + allowance.getSpender(),
                ACCOUNT_ID,
                "gte:1000",
                OWNER,
                "false",
                TOKEN_ID,
                "gt:1000",
                LIMIT,
                "1",
                ORDER,
                "ASC");

        // Creating the rest client with the uri variables
        RestClient restClient = RestClient.builder()
                .baseUrl(CALL_URI)
                .defaultUriVariables(uriVariables)
                .build();

        // Performing the GET operation
        var result = restClient
                .get()
                .uri("?" + accountIdParam + ownerParam + tokenIdParam + limitParam + orderParam)
                .retrieve()
                .body(NftAllowancesResponse.class);

        assertEquals(result.getAllowances(), mapper.map(List.of(allowance)));
        assertEquals(
                result.getLinks().getNext(),
                "/api/v1/accounts/" + allowance.getSpender()
                        + "/allowances/nfts?limit=1&order=asc&account.id=gte:" + EntityId.of(allowance.getOwner())
                        + "&token.id=gt:" + EntityId.of(allowance.getTokenId()));
    }

    @ParameterizedTest
    @CsvSource({
        "0.0.1001,1.2.3.4,0.0.2000,false,2,asc",
        "0.0.1001,0.0.2000,1.2.3.4,false,2,asc",
        "0.0.1001,0.0.3000,0.0.2000,false,-1,asc",
        "0.0.1001,0.0.3000,0.0.2000,false,111,asc",
        "0.0.1001,0.0.3000,0.0.2000,false,3,ttt",
        "0.0.1001,gee:0.0.3000,0.0.2000,false,3,asc",
        "9223372036854775807,0.0.3000,0.0.2000,false,3,asc",
        "0x00000001000000000000000200000000000000034,0.0.3000,0.0.2000,false,3,asc"
    })
    void failWithInvalidParams(String id, String accountId, String tokenId, String owner, String limit, String order) {
        Map<String, String> uriVariables =
                Map.of("id", id, ACCOUNT_ID, accountId, OWNER, owner, TOKEN_ID, tokenId, LIMIT, limit, ORDER, order);

        RestClient restClient = RestClient.builder()
                .baseUrl(CALL_URI)
                .defaultUriVariables(uriVariables)
                .build();

        // Performing the GET operation
        assertThrows(HttpClientErrorException.BadRequest.class, () -> restClient
                .get()
                .uri("?" + accountIdParam + ownerParam + tokenIdParam + limitParam + orderParam)
                .retrieve()
                .body(NftAllowancesResponse.class));
    }

    @Test
    void failTokenIdPresentWithoutAccount() {
        Map<String, String> uriVariables =
                Map.of("id", "0.0.1000", OWNER, "true", TOKEN_ID, "gt:1000", LIMIT, "1", ORDER, "asc");

        RestClient restClient = RestClient.builder()
                .baseUrl(CALL_URI)
                .defaultUriVariables(uriVariables)
                .build();

        // Performing the GET operation
        assertThrows(HttpClientErrorException.BadRequest.class, () -> restClient
                .get()
                .uri("?" + ownerParam + tokenIdParam + limitParam + orderParam)
                .retrieve()
                .body(NftAllowancesResponse.class));
    }
}
