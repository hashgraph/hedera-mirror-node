package com.hedera.mirror.importer.downloader.provider;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.ConsensusNodeStub;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.StreamSourceProperties;

@ExtendWith(MockitoExtension.class)
class CompositeStreamFileProviderTest {

    private static final StreamFileData DATA = StreamFileData.from(StreamFilename.EPOCH.getFilename(), "");
    private static final StreamFilename FILENAME = StreamFilename.EPOCH;
    private static final ConsensusNode NODE = ConsensusNodeStub.builder().build();

    private CommonDownloaderProperties properties;
    private StreamFileProvider compositeStreamFileProvider;

    @Mock
    private StreamFileProvider streamFileProvider1;

    @Mock
    private StreamFileProvider streamFileProvider2;

    @BeforeEach
    void setup() {
        properties = new CommonDownloaderProperties(new MirrorProperties());
        properties.getSources().add(new StreamSourceProperties());
        properties.getSources().add(new StreamSourceProperties());
        compositeStreamFileProvider = new CompositeStreamFileProvider(properties, List.of(streamFileProvider1,
                streamFileProvider2));
    }

    @Test
    void get() {
        when(streamFileProvider1.get(NODE, FILENAME)).thenReturn(Mono.just(DATA));
        compositeStreamFileProvider.get(NODE, FILENAME)
                .as(StepVerifier::create)
                .expectNext(DATA)
                .expectComplete()
                .verify(Duration.ofMillis(250));
    }

    @Test
    void getRecovers() {
        when(streamFileProvider1.get(NODE, FILENAME)).thenReturn(Mono.error(new IllegalStateException("error")));
        when(streamFileProvider2.get(NODE, FILENAME)).thenReturn(Mono.just(DATA));
        compositeStreamFileProvider.get(NODE, FILENAME)
                .as(StepVerifier::create)
                .expectNext(DATA)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }

    @Test
    void getNoSuchKeyException() {
        var error = NoSuchKeyException.builder().message("No key").build();
        when(streamFileProvider1.get(NODE, FILENAME)).thenReturn(Mono.error(error));
        compositeStreamFileProvider.get(NODE, FILENAME)
                .as(StepVerifier::create)
                .expectError(NoSuchKeyException.class)
                .verify(Duration.ofMillis(250));
    }

    @Test
    void getSourcesExhausted() {
        var finalError = new IllegalStateException("error2");
        when(streamFileProvider1.get(NODE, FILENAME)).thenReturn(Mono.error(new IllegalStateException("error1")));
        when(streamFileProvider2.get(NODE, FILENAME)).thenReturn(Mono.error(finalError));
        compositeStreamFileProvider.get(NODE, FILENAME)
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t).isEqualTo(finalError))
                .verify(Duration.ofMillis(500));

        // Ensure at least one source always remains
        Mockito.reset(streamFileProvider1, streamFileProvider2);
        when(streamFileProvider2.get(NODE, FILENAME)).thenReturn(Mono.just(DATA));
        compositeStreamFileProvider.get(NODE, FILENAME)
                .as(StepVerifier::create)
                .expectNext(DATA)
                .expectComplete()
                .verify(Duration.ofMillis(250));
    }

    @Test
    void getSingleSource() {
        compositeStreamFileProvider = new CompositeStreamFileProvider(properties, List.of(streamFileProvider1));
        var error = new RuntimeException("error");
        when(streamFileProvider1.get(NODE, FILENAME)).thenReturn(Mono.error(error));
        compositeStreamFileProvider.get(NODE, FILENAME)
                .as(StepVerifier::create)
                .expectErrorSatisfies(t -> assertThat(t).isEqualTo(error))
                .verify(Duration.ofMillis(500));
    }

    @Test
    void getBackoffRecovers() {
        properties.getSources().get(0).setBackoff(Duration.ofMillis(500L));
        when(streamFileProvider1.get(NODE, FILENAME)).thenReturn(Mono.error(new IllegalStateException("error1")));
        when(streamFileProvider2.get(NODE, FILENAME)).thenReturn(Mono.error(new IllegalStateException("error2")));

        compositeStreamFileProvider.get(NODE, FILENAME)
                .as(StepVerifier::create)
                .expectError(IllegalStateException.class)
                .verify(Duration.ofMillis(500));

        Mockito.reset(streamFileProvider1, streamFileProvider2);
        when(streamFileProvider1.get(NODE, FILENAME))
                .thenReturn(Mono.just(DATA));

        Mono.delay(Duration.ofMillis(1000L))
                .then(compositeStreamFileProvider.get(NODE, FILENAME))
                .as(StepVerifier::create)
                .expectNext(DATA)
                .expectComplete()
                .verify(Duration.ofMillis(2000));
    }

    @Test
    void list() {
        when(streamFileProvider1.list(NODE, FILENAME)).thenReturn(Flux.just(DATA));
        compositeStreamFileProvider.list(NODE, FILENAME)
                .as(StepVerifier::create)
                .expectNext(DATA)
                .expectComplete()
                .verify(Duration.ofMillis(250));
    }

    @Test
    void listRecovers() {
        when(streamFileProvider1.list(NODE, FILENAME)).thenReturn(Flux.error(new IllegalStateException("error")));
        when(streamFileProvider2.list(NODE, FILENAME)).thenReturn(Flux.just(DATA));
        compositeStreamFileProvider.list(NODE, FILENAME)
                .as(StepVerifier::create)
                .expectNext(DATA)
                .expectComplete()
                .verify(Duration.ofMillis(500));
    }
}
