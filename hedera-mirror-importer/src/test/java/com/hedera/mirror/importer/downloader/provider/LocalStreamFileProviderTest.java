/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.provider;

import static com.hedera.mirror.importer.ImporterProperties.STREAMS;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.FileCopier;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.test.StepVerifier;

class LocalStreamFileProviderTest extends AbstractStreamFileProviderTest {

    private final LocalStreamFileProperties localProperties = new LocalStreamFileProperties();

    @Override
    protected String getProviderPathSeparator() {
        return File.separator;
    }

    @Override
    protected String resolveProviderRelativePath(ConsensusNode node, String fileName) {
        return Path.of(
                        StreamType.RECORD.getPath(),
                        StreamType.RECORD.getNodePrefix() + node.getNodeAccountId(),
                        fileName)
                .toString();
    }

    @BeforeEach
    void setup() throws Exception {
        super.setup();
        streamFileProvider = new LocalStreamFileProvider(properties, localProperties);
    }

    @Override
    protected FileCopier createFileCopier(Path dataPath) {
        var fromPath = Path.of("data", "recordstreams", "v6");
        return FileCopier.create(TestUtils.getResource(fromPath.toString()).toPath(), dataPath)
                .to(STREAMS, StreamType.RECORD.getPath());
    }

    @Test
    void listAll() {
        var node = node("0.0.3");
        var fileCopier = getFileCopier(node);
        fileCopier.copy();
        var sigs = streamFileProvider
                .list(node, StreamFilename.EPOCH)
                .collectList()
                .block();
        assertThat(sigs).hasSize(2);
        sigs.forEach(
                sig -> streamFileProvider.get(node, sig.getStreamFilename()).block());
    }

    @Test
    void listByDay() {
        var node = node("0.0.3");
        createSignature("2022-07-13", "recordstreams", "record0.0.3", "2022-07-13T08_46_08.041986003Z.rcd_sig");
        createSignature("2022-07-13", "recordstreams", "record0.0.3", "2022-07-13T08_46_11.304284003Z.rcd_sig");
        var sigs = streamFileProvider
                .list(node, StreamFilename.EPOCH)
                .collectList()
                .block();
        assertThat(sigs).hasSize(2);
        sigs.forEach(
                sig -> streamFileProvider.get(node, sig.getStreamFilename()).block());
    }

    @Test
    void listByDayAuto() {
        properties.setPathType(PathType.AUTO);
        var node = node("0.0.3");
        createSignature("2022-07-13", "demo", "0", "0", "record", "2022-07-13T23_59_59.304284003Z.rcd_sig");
        createSignature("2022-07-14", "demo", "0", "0", "record", "2022-07-14T00_01_01.203216501Z.rcd_sig");
        var sigs = streamFileProvider
                .list(node, StreamFilename.EPOCH)
                .collectList()
                .block();
        assertThat(sigs).hasSize(2);
        sigs.forEach(
                sig -> streamFileProvider.get(node, sig.getStreamFilename()).block());
    }

    @Test
    void listAcrossDays() {
        var node = node("0.0.3");
        createSignature("2022-07-13", "recordstreams", "record0.0.3", "2022-07-13T23_59_59.304284003Z.rcd_sig");
        createSignature("2022-07-14", "recordstreams", "record0.0.3", "2022-07-14T00_01_01.203216501Z.rcd_sig");
        var sigs = streamFileProvider
                .list(node, StreamFilename.EPOCH)
                .collectList()
                .block();
        assertThat(sigs).hasSize(2);
        sigs.forEach(
                sig -> streamFileProvider.get(node, sig.getStreamFilename()).block());
    }

    @Test
    void listStartsAtNextDay() throws Exception {
        localProperties.setDeleteAfterProcessing(false);
        var node = node("0.0.3");
        var previous =
                createSignature("2022-07-13", "recordstreams", "record0.0.3", "2022-07-13T23_59_59.304284003Z.rcd_sig");
        var expected =
                createSignature("2022-07-14", "recordstreams", "record0.0.3", "2022-07-14T00_01_01.203216501Z.rcd_sig");
        var sigs = streamFileProvider
                .list(node, StreamFilename.from(previous.getPath()))
                .collectList()
                .block();
        assertThat(sigs).hasSize(1).extracting(StreamFileData::getFilename).containsExactly(expected.getName());
        sigs.forEach(
                sig -> streamFileProvider.get(node, sig.getStreamFilename()).block());
        assertThat(Files.walk(dataPath)
                        .filter(p ->
                                p.toString().contains(node.getNodeAccountId().toString()))
                        .filter(p -> !p.toString().contains("sidecar"))
                        .filter(p -> p.toFile().isFile()))
                .hasSize(2);
    }

    @Test
    void listDeletesFiles() throws Exception {
        localProperties.setDeleteAfterProcessing(true);
        var node = node("0.0.3");
        getFileCopier(node).copy();
        var lastFilename = StreamFilename.from(Instant.now().toString().replace(':', '_') + ".rcd.gz");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, lastFilename))
                .thenAwait(Duration.ofSeconds(10))
                .expectNextCount(0)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
        assertThat(Files.walk(dataPath)
                        .filter(p ->
                                p.toString().contains(node.getNodeAccountId().toString()))
                        .filter(p -> !p.toString().contains("sidecar"))
                        .noneMatch(p -> p.toFile().isFile()))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(PathType.class)
    void listAllPathTypes(PathType pathType) {
        properties.setPathType(pathType);

        if (pathType == PathType.ACCOUNT_ID) {
            fileCopier.copy();
        } else {
            fileCopier.copyAsNodeIdStructure(
                    Path::getParent, properties.getImporterProperties().getNetwork());
        }

        var accountId = "0.0.3";
        var node = node(accountId);
        var data1 = streamFileData(node, "2022-07-13T08_46_08.041986003Z.rcd_sig");
        var data2 = streamFileData(node, "2022-07-13T08_46_11.304284003Z.rcd_sig");
        StepVerifier.withVirtualTime(() -> streamFileProvider.list(node, StreamFilename.EPOCH))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNext(data1)
                .expectNext(data2)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @SneakyThrows
    private File createSignature(String... paths) {
        var subPath = Path.of("", paths);
        var streamsDir = properties.getImporterProperties().getStreamPath();
        var file = streamsDir.resolve(subPath).toFile();
        file.getParentFile().mkdirs();
        file.createNewFile();
        return file;
    }
}
