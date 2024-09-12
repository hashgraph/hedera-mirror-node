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

import static com.hedera.mirror.common.domain.token.TokenTypeEnum.FUNGIBLE_COMMON;
import static com.hedera.mirror.common.domain.token.TokenTypeEnum.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.BaseEncoding;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.rest.model.Links;
import com.hedera.mirror.rest.model.TokenAirdropsResponse;
import com.hedera.mirror.restjava.mapper.TokenAirdropsMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
class TokenAirdropsControllerTest extends ControllerTest {

    private final TokenAirdropsMapper mapper;

    @DisplayName("/api/v1/accounts/{id}/airdrops/outstanding")
    @Nested
    class OutstandingTokenAirdropsEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "accounts/{id}/airdrops/outstanding";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            var tokenAirdrop = domainBuilder.tokenAirdrop(FUNGIBLE_COMMON).persist();
            return uriSpec.uri("", tokenAirdrop.getSenderAccountId());
        }

        @ValueSource(strings = {"1000", "0.1000", "0.0.1000"})
        @ParameterizedTest
        void entityId(String id) {
            // Given
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(1000L))
                    .persist();

            // When
            var response = restClient.get().uri("", id).retrieve().toEntity(TokenAirdropsResponse.class);

            // Then
            assertThat(response.getBody().getAirdrops().getFirst()).isEqualTo(mapper.map(tokenAirdrop));
            // Based on application.yml response headers configuration
            assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=1");
        }

        @Test
        void evmAddress() {
            // Given
            var entity = domainBuilder.entity().persist();
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId()))
                    .persist();

            // When
            var response = restClient
                    .get()
                    .uri("", DomainUtils.bytesToHex(entity.getEvmAddress()))
                    .retrieve()
                    .toEntity(TokenAirdropsResponse.class);

            // Then
            assertThat(response.getBody().getAirdrops().getFirst()).isEqualTo(mapper.map(tokenAirdrop));
        }

        @Test
        void alias() {
            // Given
            var entity = domainBuilder.entity().persist();
            var tokenAirdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId()))
                    .persist();

            // When
            var response = restClient
                    .get()
                    .uri("", BaseEncoding.base32().omitPadding().encode(entity.getAlias()))
                    .retrieve()
                    .toEntity(TokenAirdropsResponse.class);

            // Then
            assertThat(response.getBody().getAirdrops().getFirst()).isEqualTo(mapper.map(tokenAirdrop));
        }

        @Test
        void followAscendingOrderLink() {
            // Given
            var entity = domainBuilder.entity().persist();
            var id = entity.getId();
            var tokenAirdrop1 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId()))
                    .persist();
            var tokenAirdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId()))
                    .persist();
            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(id);

            // When
            var result = restClient.get().uri("?limit=1", id).retrieve().body(TokenAirdropsResponse.class);
            var nextParams = "?limit=1&receiver.id=gte:%s&token.id=gt:%s"
                    .formatted(
                            EntityId.of(tokenAirdrop1.getReceiverAccountId()), EntityId.of(tokenAirdrop1.getTokenId()));

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(tokenAirdrop1), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, id).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = "?limit=1&receiver.id=gte:%s&token.id=gt:%s"
                    .formatted(
                            EntityId.of(tokenAirdrop2.getReceiverAccountId()), EntityId.of(tokenAirdrop2.getTokenId()));
            assertThat(result).isEqualTo(getExpectedResponse(List.of(tokenAirdrop2), baseLink + nextParams));

            // When follow link 2
            result = restClient
                    .get()
                    .uri(nextParams, tokenAirdrop1.getReceiverAccountId())
                    .retrieve()
                    .body(TokenAirdropsResponse.class);
            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        // TODO fix this test
        @Test
        void followDescendingOrderLink() {
            // Given
            long sender = 1000;
            long receiver = 2000;
            long fungibleTokenId = 100;
            long token1 = 300;
            long token2 = 301;

            var airdrop1 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .tokenId(fungibleTokenId))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .tokenId(token1))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(sender)
                            .receiverAccountId(receiver)
                            .tokenId(token2))
                    .persist();
            domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.receiverAccountId(receiver))
                    .persist();
            domainBuilder
                    .tokenAirdrop(NON_FUNGIBLE_UNIQUE)
                    .customize(a -> a.receiverAccountId(receiver))
                    .persist();

            var uriParams = "?limit=1&receiver.id=gte:%s&order=desc".formatted(receiver);
            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(sender);

            // When
            var result = restClient.get().uri(uriParams, sender).retrieve().body(TokenAirdropsResponse.class);
            // The first receiver id is '2000' instead of 0.0.2000 because the link creation does not alter the original
            // value sent in the request
            // The second receiver id is added by the link generator and has shard.realm.num format
            var nextParams = "?limit=1&receiver.id=gte:2000&receiver.id=lte:0.0.2000&order=desc&token.id=lt:0.0.301";

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = "?limit=1&receiver.id=gte:2000&receiver.id=lte:0.0.2000&order=desc&token.id=lt:0.0.300";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = "?limit=1&receiver.id=gte:2000&receiver.id=lte:0.0.2000&order=desc&token.id=lt:0.0.100";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop1), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, sender).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(), null));
        }

        // TODO fix this test
        @Test
        void paginationReceiverAndToken() {
            // Given
            var entity = domainBuilder.entity().persist();
            var airdrop = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .tokenId(5L))
                    .persist();
            var airdrop2 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(3L)
                            .tokenId(6L))
                    .persist();
            var airdrop3 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(4L)
                            .tokenId(5L))
                    .persist();
            var airdrop4 = domainBuilder
                    .tokenAirdrop(FUNGIBLE_COMMON)
                    .customize(a -> a.senderAccountId(entity.getId())
                            .receiverAccountId(5L)
                            .tokenId(6L))
                    .persist();

            var baseLink = "/api/v1/accounts/%d/airdrops/outstanding".formatted(entity.getId());
            var uriParams = "?limit=1&receiver.id=gt:2&token.id=gt:4";
            var nextParams = "?limit=1&receiver.id=gte:0.0.3&token.id=gt:0.0.5";

            // When
            var result =
                    restClient.get().uri(uriParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = "?limit=1&receiver.id=gte:0.0.3&token.id=gt:0.0.6";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop2), baseLink + nextParams));

            // When follow link
            result = restClient.get().uri(nextParams, entity.getId()).retrieve().body(TokenAirdropsResponse.class);

            // Then
            nextParams = "?limit=1&receiver.id=gte:0.0.3&token.id=gt:0.0.5";
            assertThat(result).isEqualTo(getExpectedResponse(List.of(airdrop3), baseLink + nextParams));
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
                    () -> restClient.get().uri("", accountId).retrieve().body(TokenAirdropsResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.NotFound.class, "No account found for the given ID");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc",
                    "a.b.c",
                    "0.0.",
                    "0.65537.1001",
                    "0.0.-1001",
                    "9223372036854775807",
                    "0x00000001000000000000000200000000000000034"
                })
        void invalidId(String id) {
            // When
            ThrowingCallable callable =
                    () -> restClient.get().uri("", id).retrieve().body(TokenAirdropsResponse.class);

            // Then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'id' with value: '" + id + "'");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc",
                    "a.b.c",
                    "0.0.",
                    "0.65537.1001",
                    "0.0.-1001",
                    "9223372036854775807",
                    "0x00000001000000000000000200000000000000034"
                })
        void invalidAccountId(String accountId) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?receiver.id={accountId}", "0.0.1001", accountId)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'receiver.id' with value: '" + accountId + "'");
        }

        @ParameterizedTest
        @CsvSource({
            "101, limit must be less than or equal to 100",
            "-1, limit must be greater than 0",
            "a, Failed to convert 'limit' with value: 'a'"
        })
        void invalidLimit(String limit, String expected) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?limit={limit}", "0.0.1001", limit)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.BadRequest.class, expected);
        }

        @ParameterizedTest
        @CsvSource({
            "ascending, Failed to convert 'order' with value: 'ascending'",
            "dsc, Failed to convert 'order' with value: 'dsc'",
            "invalid, Failed to convert 'order' with value: 'invalid'"
        })
        void invalidOrder(String order, String expected) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?order={order}", "0.0.1001", order)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(callable, HttpClientErrorException.BadRequest.class, expected);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "abc",
                    "a.b.c",
                    "0.0.",
                    "0.65537.1001",
                    "0.0.-1001",
                    "9223372036854775807",
                    "0x00000001000000000000000200000000000000034"
                })
        void invalidTokenId(String tokenId) {
            // When
            ThrowingCallable callable = () -> restClient
                    .get()
                    .uri("?token.id={tokenId}", "0.0.1001", tokenId)
                    .retrieve()
                    .body(TokenAirdropsResponse.class);

            // Then
            validateError(
                    callable,
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'token.id' with value: '" + tokenId + "'");
        }

        //        @ParameterizedTest
        //        @CsvSource({
        //                "?token.id=gt:0.0.1000&token.id=lt:0.0.2000,Only one occurrence of token.id",
        //        })
        //        void invalidNumberOfParameters(String uri) {
        //            // When
        //            ThrowingCallable callable = () -> restClient
        //                    .get()
        //                    .uri(uri, "0.0.1001")
        //                    .retrieve()
        //                    .body(TokenAirdropsResponse.class);
        //
        //            // Then
        //            validateError(
        //                    callable,
        //                    HttpClientErrorException.BadRequest.class,
        //                    "Failed to convert 'token.id' with value: ");
        //        }
    }

    private TokenAirdropsResponse getExpectedResponse(List<TokenAirdrop> tokenAirdrops, String next) {
        return new TokenAirdropsResponse().airdrops(mapper.map(tokenAirdrops)).links(new Links().next(next));
    }
}
