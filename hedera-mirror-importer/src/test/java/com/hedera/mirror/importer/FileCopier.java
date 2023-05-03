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
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

public interface FileCopier {

    FileFilter ALL_FILTER = f -> true;

    static FileCopier create(Path from, Path to) {
        return create(from, to, PathType.ACCOUNT_ID, StreamType.RECORD, Collections.emptySet());
    }

    static FileCopier create(Path from, Path to, StreamType streamType) {
        return create(from, to, PathType.ACCOUNT_ID, streamType, Collections.emptySet());
    }

    static FileCopier create(Path from, Path to, PathType pathType, StreamType streamType, Set<String> copyOnlyDirs) {
        return SingleDestinationFileCopier.create(from, to, ALL_FILTER, ALL_FILTER, pathType, streamType, copyOnlyDirs);
    }

    FileCopier from(Path source);

    FileCopier from(String... source);

    FileCopier filterDirectories(FileFilter newDirFilter);

    FileCopier filterDirectories(String wildcardPattern);

    FileCopier filterFiles(FileFilter newFileFilter);

    FileCopier filterFiles(String wildcardPattern);

    FileCopier to(Path target);

    FileCopier to(String... target);

    Path getTo();

    Path getTo(ConsensusNode node);

    Path getFrom();

    Path getNodePath(ConsensusNode node);

    void copy();
}
