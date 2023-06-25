/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import java.io.FileFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Function;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;

@Log4j2
@Value
public class FileCopier {

    private static final FileFilter ALL_FILTER = f -> true;

    private final Path from;
    private final Path to;
    private final FileFilter dirFilter;
    private final FileFilter fileFilter;

    private FileCopier(
            @NonNull Path from, @NonNull Path to, @NonNull FileFilter dirFilter, @NonNull FileFilter fileFilter) {
        this.from = from;
        this.to = to;
        this.dirFilter = dirFilter;
        this.fileFilter = fileFilter;
    }

    public static FileCopier create(Path from, Path to) {
        return new FileCopier(from, to, ALL_FILTER, ALL_FILTER);
    }

    public FileCopier from(Path source) {
        return new FileCopier(from.resolve(source), to, dirFilter, fileFilter);
    }

    public FileCopier from(String... source) {
        return from(Paths.get("", source));
    }

    public FileCopier filterDirectories(FileFilter newDirFilter) {
        FileFilter andFilter =
                dirFilter == ALL_FILTER ? newDirFilter : f -> dirFilter.accept(f) || newDirFilter.accept(f);
        return new FileCopier(from, to, andFilter, fileFilter);
    }

    public FileCopier filterDirectories(String wildcardPattern) {
        return filterDirectories(
                WildcardFileFilter.builder().setWildcards(wildcardPattern).get());
    }

    public FileCopier filterFiles(FileFilter newFileFilter) {
        FileFilter andFilter =
                fileFilter == ALL_FILTER ? newFileFilter : f -> fileFilter.accept(f) || newFileFilter.accept(f);
        return new FileCopier(from, to, dirFilter, andFilter);
    }

    public FileCopier filterFiles(String wildcardPattern) {
        return filterFiles(
                WildcardFileFilter.builder().setWildcards(wildcardPattern).get());
    }

    public FileCopier to(Path target) {
        return new FileCopier(from, to.resolve(target), dirFilter, fileFilter);
    }

    public FileCopier to(String... target) {
        return to(Paths.get("", target));
    }

    public void copy() {
        try {
            log.debug("Copying {} to {}", from, to);
            FileFilter combinedFilter = f -> f.isDirectory() ? dirFilter.accept(f) : fileFilter.accept(f);
            FileUtils.copyDirectory(from.toFile(), to.toFile(), combinedFilter);

            if (log.isTraceEnabled()) {
                try (var paths = Files.walk(to)) {
                    paths.forEach(p -> log.trace("Moved: {}", p));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Copy a {@code from} account ID based directory structure to the destination as a node ID based structure.
     *
     * @param destinationAdjuster adjustment to make to {@code to} path. Often {@code to} as been configured to be a
     *                            specific account ID directory stream type, such as {@code accountBalances} or
     *                            {@code recordstreams} etc. Provide a function to adjust this prior to files being
     *                            copied. If no adjustment is required, provide {@link Function.identity()}
     * @param network             Hedera or dev/test network name, typically found in mirror node properties.
     */
    public void copyAsNodeIdStructure(Function<Path, Path> destinationAdjuster, String network) {

        try {
            var destination = destinationAdjuster.apply(to);
            var networkDir = destination.resolve(network);
            var sourceNodeDirs = FileUtils.listFilesAndDirs(from.toFile(), TrueFileFilter.INSTANCE, null);

            for (var sourceNodeDir : sourceNodeDirs) {

                var sourceNodeDirName = sourceNodeDir.getName();
                var streamTypeOpt = Arrays.stream(StreamType.values())
                        .filter(st -> sourceNodeDirName.startsWith(st.getNodePrefix()))
                        .findFirst();

                if (streamTypeOpt.isPresent()) {
                    var streamType = streamTypeOpt.get();
                    EntityId nodeEntityId = EntityId.of(
                            sourceNodeDirName.substring(
                                    streamType.getNodePrefix().length()),
                            EntityType.ACCOUNT);

                    var destinationNodeIdPath = networkDir.resolve(Path.of(
                            String.valueOf(nodeEntityId.getShardNum()),
                            String.valueOf(nodeEntityId.getEntityNum() - 3L), // Node ID
                            streamType.getNodeIdBasedSuffix()));

                    FileUtils.copyDirectory(sourceNodeDir, destinationNodeIdPath.toFile());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
