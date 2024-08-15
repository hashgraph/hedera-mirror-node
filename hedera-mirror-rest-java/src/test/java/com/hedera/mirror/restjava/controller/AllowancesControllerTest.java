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

import com.google.common.io.BaseEncoding;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.rest.model.Links;
import com.hedera.mirror.rest.model.NftAllowancesResponse;
import com.hedera.mirror.restjava.mapper.NftAllowanceMapper;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
class AllowancesControllerTest extends ControllerTest {

    private final NftAllowanceMapper mapper;

    @DisplayName("/api/v1/accounts/{id}/allowances/nfts")
    @Nested
    class NftAllowanceEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "accounts/{id}/allowances/nfts";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            var entity = domainBuilder.entity().persist();
            var allowance = nftAllowance(a -> a.owner(entity.getId()));
            return uriSpec.uri("", allowance.getOwner());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void entityId(boolean persistEntity) {
            // Given
            var entityBuilder = domainBuilder.entity();
            var entity = persistEntity ? entityBuilder.persist() : entityBuilder.get();
            var allowance1 = nftAllowance(a -> a.owner(entity.getId()));
            var allowance2 = nftAllowance(a -> a.owner(allowance1.getOwner()));

            // When
            var response =
                    restClient.get().uri("", allowance1.getOwner()).retrieve().toEntity(NftAllowancesResponse.class);

            // Then
            assertThat(response.getBody()).isEqualTo(getExpectedResponse(List.of(allowance1, allowance2), null));
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=1");
        }

        @Test
        void followAscendingOrderLink() {
            // Given
            var entityBuilder = domainBuilder.entity();
            var entity = entityBuilder.persist();
            var allowance1 = nftAllowance(a -> a.owner(entity.getId()));
            var allowance2 = nftAllowance(a -> a.owner(allowance1.getOwner()));
            var baseLink = "/api/v1/accounts/%d/allowances/nfts".formatted(allowance1.getOwner());

            // When
            var result = restClient
                    .get()
                    .uri("?limit=1", allowance1.getOwner())
                    .retrieve()
                    .body(NftAllowancesResponse.class);
            var nextParams = "?limit=1&account.id=gte:%s&token.id=gt:%s"
                    .formatted(EntityId.of(allowance1.getSpender()), EntityId.of(allowance1.getTokenId()));

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1), baseLink + nextParams));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextParams, allowance1.getOwner())
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            nextParams = "?limit=1&account.id=gte:%s&token.id=gt:%s"
                    .formatted(EntityId.of(allowance2.getSpender()), EntityId.of(allowance2.getTokenId()));
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance2), baseLink + nextParams));

            // When follow link 2
            result = restClient
                    .get()
                    .uri(nextParams, allowance1.getOwner())
                    .retrieve()
                    .body(NftAllowancesResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @Test
        void evmAddress() {
            // Given
            var entity = domainBuilder.entity().persist();
            var allowance1 = nftAllowance(a -> a.owner(entity.getId()));
            var allowance2 = nftAllowance(a -> a.owner(allowance1.getOwner()));

            // When
            var result = restClient
                    .get()
                    .uri("", DomainUtils.bytesToHex(entity.getEvmAddress()))
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1, allowance2), null));
        }

        @Test
        void alias() {
            // Given
            var entity = domainBuilder.entity().persist();
            var allowance1 = nftAllowance(a -> a.owner(entity.getId()));
            var allowance2 = nftAllowance(a -> a.owner(allowance1.getOwner()));

            // When
            var result = restClient
                    .get()
                    .uri("", BaseEncoding.base32().omitPadding().encode(entity.getAlias()))
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1, allowance2), null));
        }

        @Test
        void orderAscending() {
            // Given
            var entity = domainBuilder.entity().persist();
            var allowance1 = nftAllowance(a -> a.owner(entity.getId()));
            nftAllowance(a -> a.owner(allowance1.getOwner()));
            var uriParams = "?account.id=gte:%s&account.id=lt:%s&owner=true&token.id=gt:%s&limit=1&order=asc"
                    .formatted(
                            EntityId.of(allowance1.getSpender() - 1),
                            EntityId.of(allowance1.getSpender() + 1),
                            EntityId.of(allowance1.getTokenId() - 1));
            var next =
                    "/api/v1/accounts/%s/allowances/nfts?account.id=lt:%s&account.id=gte:%s&owner=true&limit=1&order=asc&token.id=gt:%s"
                            .formatted(
                                    allowance1.getOwner(),
                                    EntityId.of(allowance1.getSpender() + 1),
                                    EntityId.of(allowance1.getSpender()),
                                    EntityId.of(allowance1.getTokenId()));

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, allowance1.getOwner())
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1), next));
        }

        @ParameterizedTest
        @EnumSource(EntityIdType.class)
        void allRangeConditions(EntityIdType type) {
            // Given
            var entity = domainBuilder.entity().persist();
            var allowance1 = nftAllowance(a -> a.owner(entity.getId()));
            var allowance2 = nftAllowance(a -> a.owner(allowance1.getOwner()));
            var spender1 = type.idExtractor.apply(EntityId.of(allowance1.getSpender()));
            var spender2 = type.idExtractor.apply(EntityId.of(allowance2.getSpender()));
            var token1 = type.idExtractor.apply(EntityId.of(allowance1.getTokenId()));
            var token2 = type.idExtractor.apply(EntityId.of(allowance2.getTokenId() + 1));
            var uriParams =
                    "?account.id=gte:%s&account.id=lte:%s&owner=true&token.id=gt:%s&token.id=lt:%s&limit=1&order=desc"
                            .formatted(spender1, spender2, token1, token2);
            var next =
                    "/api/v1/accounts/%s/allowances/nfts?account.id=gte:%s&account.id=lte:%s&owner=true&token.id=gt:%s&token.id=lt:%s&limit=1&order=desc"
                            .formatted(
                                    allowance1.getOwner(),
                                    spender1,
                                    EntityId.of(allowance2.getSpender()),
                                    token1,
                                    EntityId.of(allowance2.getTokenId()));

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, allowance1.getOwner())
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance2), next));
        }

        @Test
        void noOperators() {
            // Given
            var entity = domainBuilder.entity().persist();
            var allowance1 = nftAllowance(a -> a.owner(entity.getId()));
            var allowance2 = nftAllowance(a -> a.owner(allowance1.getOwner()));
            var uriParams = "?account.id={account.id}&limit=1&order=asc";
            var nextLink = "/api/v1/accounts/%s/allowances/nfts?account.id=%s&limit=1&order=asc&token.id=gt:%s"
                    .formatted(
                            allowance2.getOwner(),
                            EntityId.of(allowance2.getSpender()),
                            EntityId.of(allowance2.getTokenId()));

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, allowance2.getOwner(), EntityId.of(allowance2.getSpender()))
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance2), nextLink));
        }

        @Test
        void orderDescending() {
            var entity = domainBuilder.entity().persist();
            var allowance1 = nftAllowance(a -> a.owner(entity.getId()));
            var allowance2 = nftAllowance(a -> a.owner(allowance1.getOwner()));
            var allowance3 = nftAllowance(a -> a.owner(allowance1.getOwner()));
            var uriParams = "?account.id=gte:%s&owner=true&token.id=gt:%s&limit=2&order=desc"
                    .formatted(EntityId.of(allowance2.getSpender()), EntityId.of(allowance2.getTokenId() - 1));

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, allowance1.getOwner())
                    .retrieve()
                    .body(NftAllowancesResponse.class);
            var next =
                    "/api/v1/accounts/%s/allowances/nfts?account.id=gte:%s&account.id=lte:%s&owner=true&token.id=gt:%s&token.id=lt:%s&limit=2&order=desc"
                            .formatted(
                                    allowance2.getOwner(),
                                    EntityId.of(allowance2.getSpender()),
                                    EntityId.of(allowance2.getSpender()),
                                    EntityId.of(allowance2.getTokenId() - 1),
                                    EntityId.of(allowance2.getTokenId()));

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance3, allowance2), next));
        }

        @Test
        void ownerFalse() {
            var entity = domainBuilder.entity().persist();
            var allowance1 = nftAllowance(a -> a.spender(entity.getId()));
            nftAllowance(a -> a.spender(allowance1.getSpender()));
            var uriParams = "?account.id=gte:0.0.1000&owner=false&token.id=gt:0.0.1000&limit=1&order=asc";
            var next =
                    "/api/v1/accounts/%s/allowances/nfts?owner=false&limit=1&order=asc&account.id=gte:%s&token.id=gt:%s"
                            .formatted(
                                    allowance1.getSpender(),
                                    EntityId.of(allowance1.getOwner()),
                                    EntityId.of(allowance1.getTokenId()));

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, allowance1.getSpender())
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1), next));
        }

        @Test
        void emptyNextLink() {
            // Given
            var entity = domainBuilder.entity().persist();
            var allowance1 = nftAllowance(a -> a.spender(entity.getId()));
            nftAllowance(a -> a.owner(allowance1.getOwner()));
            var uriParams = "?account.id=gte:0.0.5000&owner=true&token.id=gt:0.0.5000&limit=1&order=asc";

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, allowance1.getSpender())
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        @ParameterizedTest
        @CsvSource({
            "0.0.,0.0.1002,0.0.2000,false,2,asc",
            "0.0.1001,1.2.3.4,0.0.2000,false,2,asc",
            "0.65537.1001,1.2.3,0.0.2000,false,2,asc",
            "0.0.-1001,1.2.3,0.0.2000,false,2,asc",
            "0.0.1001,0.0.2000,1.2.3.4,false,2,asc",
            "0.0.1001,0.0.3000,0.0.2000,false,-1,asc",
            "0.0.1001,0.0.3000,0.0.2000,false,111,asc",
            "0.0.1001,0.0.3000,0.0.2000,false,3,ttt",
            "0.0.1001,gee:0.0.3000,0.0.2000,false,3,asc",
            "null,gte:0.0.3000,0.0.2000,false,3,asc",
            "0.0.4294967296,gt:0.0.3000,gte:0.0.3000,false,3,asc",
            "9223372036854775807,0.0.3000,0.0.2000,false,3,asc",
            "0x00000001000000000000000200000000000000034,0.0.3000,0.0.2000,false,3,asc"
        })
        void invalidParameters(String id, String accountId, String tokenId, String owner, String limit, String order) {
            // Given
            var uriParams = "?account.id={accountId}&owner={owner}&token.id={token.id}&limit={limit}&order={order}";

            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri(uriParams, id, accountId, owner, tokenId, limit, order)
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.BadRequest.class, "Bad Request");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "0.0x000000000000000000000000000000000186Fb1b",
                    "0.0.0x000000000000000000000000000000000186Fb1b",
                    "0x000000000000000000000000000000000186Fb1b",
                    "0.0.AABBCC22",
                    "0.AABBCC22",
                    "AABBCC22"
                })
        void notFound(String accountId) {
            // When
            ThrowingCallable callable =
                    () -> restClient.get().uri("", accountId).retrieve().body(NftAllowancesResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.NotFound.class, "No account found for the given ID");
        }

        @ParameterizedTest
        @CsvSource({
            "?owner=true&token.id=gt:0.0.1000&limit=1&order=asc,token.id parameter must have account.id present",
            "?owner=true&account.id=gte:0.0.1000&token.id=ne:0.0.1000&limit=1&order=asc,Unsupported range operator ne for token.id",
            "?owner=true&account.id=gte:0.0.1000&account.id=lte:0.0.999&token.id=eq:0.0.1000&limit=1&order=asc,Invalid range provided for account.id",
            "?owner=true&account.id=gte:0.0.1000&token.id=gt:0.0.1000&&token.id=lt:0.0.800&limit=1&order=asc,Invalid range provided for token.id"
        })
        void invalidRange(String uriParams, String message) {
            // Given
            var entity = domainBuilder.entity().persist();
            var allowance = nftAllowance(a -> a.owner(entity.getId()));

            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri(uriParams, allowance.getOwner())
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.BadRequest.class, message);
        }

        @Test
        void tokenIdAllowedRange() {
            // Given
            var entity = domainBuilder.entity().persist();
            var allowance1 =
                    nftAllowance(a -> a.owner(entity.getId()).tokenId(700).spender(2002));
            var allowance2 =
                    nftAllowance(a -> a.owner(entity.getId()).tokenId(1002).spender(1000));
            var uriParams =
                    "?account.id=gte:0.0.1000&account.id=lte:0.0.2002&owner=true&token.id=gt:0.0.1000&&token.id=lt:0.0.800&limit=2&order=asc";
            var next =
                    "/api/v1/accounts/%s/allowances/nfts?account.id=lte:0.0.2002&account.id=gte:0.0.2002&owner=true&token.id=lt:0.0.800&token.id=gt:0.0.700&limit=2&order=asc"
                            .formatted(allowance1.getOwner());

            // When
            var result = restClient
                    .get()
                    .uri(uriParams, allowance1.getOwner())
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance2, allowance1), next));
        }

        @Test
        void succeedingExclusiveLink() {
            // Given
            var entityBuilder = domainBuilder.entity();
            var entity = entityBuilder.persist();
            var allowance1 =
                    nftAllowance(a -> a.owner(entity.getId()).spender(99700).tokenId(99800));
            var allowance2 = nftAllowance(
                    a -> a.owner(allowance1.getOwner()).spender(99701).tokenId(99800));
            var allowance3 = nftAllowance(
                    a -> a.owner(allowance1.getOwner()).spender(99702).tokenId(99800));

            var uri = "?limit=2&account.id=gte:0.0.99700&token.id=0.0.99800";
            var result =
                    restClient.get().uri(uri, allowance1.getOwner()).retrieve().body(NftAllowancesResponse.class);
            var nextLinkQueryParameters = "?limit=2&token.id=0.0.99800&account.id=gt:0.0.99701";
            var expectedLink =
                    "/api/v1/accounts/%s/allowances/nfts".formatted(allowance1.getOwner()) + nextLinkQueryParameters;

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance1, allowance2), expectedLink));

            // When follow link
            result = restClient
                    .get()
                    .uri(nextLinkQueryParameters, allowance1.getOwner())
                    .retrieve()
                    .body(NftAllowancesResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(allowance3), null));
        }

        private NftAllowance nftAllowance(Consumer<NftAllowance.NftAllowanceBuilder<?, ?>> consumer) {
            return domainBuilder
                    .nftAllowance()
                    .customize(a -> a.approvedForAll(true))
                    .customize(consumer)
                    .persist();
        }

        private NftAllowancesResponse getExpectedResponse(List<NftAllowance> nftAllowances, String next) {
            return new NftAllowancesResponse()
                    .allowances(mapper.map(nftAllowances))
                    .links(new Links().next(next));
        }

        @Getter
        @RequiredArgsConstructor
        private enum EntityIdType {
            NUM(e -> "" + e.getNum()),
            REALM_NUM(e -> e.getRealm() + "." + e.getNum()),
            SHARD_REALM_NUM(e -> e.toString());

            private final Function<EntityId, String> idExtractor;
        }
    }
}
