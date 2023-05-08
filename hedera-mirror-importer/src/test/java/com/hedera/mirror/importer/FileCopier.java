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

import java.io.FileFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
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
        return filterDirectories(new WildcardFileFilter(wildcardPattern));
    }

    public FileCopier filterFiles(FileFilter newFileFilter) {
        FileFilter andFilter =
                fileFilter == ALL_FILTER ? newFileFilter : f -> fileFilter.accept(f) || newFileFilter.accept(f);
        return new FileCopier(from, to, dirFilter, andFilter);
    }

    public FileCopier filterFiles(String wildcardPattern) {
        return filterFiles(new WildcardFileFilter(wildcardPattern));
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
}
