/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.block;

import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;

@Named
@RequiredArgsConstructor
public class BlockStreamVerifier {

    private static final BlockFile EMPTY = BlockFile.builder().build();

    private final BlockFileTransformer blockFileTransformer;
    private final RecordFileRepository recordFileRepository;
    private final StreamFileNotifier streamFileNotifier;

    private final AtomicReference<Optional<BlockFile>> lastBlockFile = new AtomicReference<>(Optional.empty());

    public Optional<Long> getLastBlockNumber() {
        return getLastBlockFile().map(BlockFile::getIndex);
    }

    public void verify(@NotNull BlockFile blockFile) {
        verifyBlockNumber(blockFile);
        verifyHashChain(blockFile);
        var recordFile = blockFileTransformer.transform(blockFile);
        streamFileNotifier.verified(recordFile);
        setLastBlockFile(blockFile);
    }

    private Optional<String> getExpectedPreviousHash() {
        return getLastBlockFile().map(BlockFile::getHash);
    }

    private Optional<BlockFile> getLastBlockFile() {
        return lastBlockFile.get().or(() -> {
            var last = recordFileRepository
                    .findLatest()
                    .map(r -> BlockFile.builder()
                            .hash(r.getHash())
                            .index(r.getIndex())
                            .build())
                    .or(() -> Optional.of(EMPTY));
            lastBlockFile.compareAndSet(Optional.empty(), last);
            return last;
        });
    }

    private void setLastBlockFile(BlockFile blockFile) {
        var copy = (BlockFile) blockFile.copy();
        copy.clear();
        lastBlockFile.set(Optional.of(copy));
    }

    private void verifyBlockNumber(BlockFile blockFile) {
        var blockNumber = blockFile.getIndex();
        getLastBlockNumber().ifPresent(lastBlockNumber -> {
            if (blockNumber != lastBlockNumber + 1) {
                throw new InvalidStreamFileException(String.format(
                        "Non-consecutive block number, previous = %d, current = %d", lastBlockNumber, blockNumber));
            }
        });

        try {
            String filename = blockFile.getName();
            int endIndex = filename.indexOf(FilenameUtils.EXTENSION_SEPARATOR);
            long actual = Long.parseLong(endIndex != -1 ? filename.substring(0, endIndex) : filename);
            if (actual != blockNumber) {
                throw new InvalidStreamFileException(String.format(
                        "Block number mismatch, from filename = %d, from content = %d", actual, blockNumber));
            }
        } catch (NumberFormatException e) {
            throw new InvalidStreamFileException("Failed to parse block number from filename " + blockFile.getName());
        }
    }

    private void verifyHashChain(BlockFile blockFile) {
        getExpectedPreviousHash().ifPresent(expected -> {
            if (!blockFile.getPreviousHash().contentEquals(expected)) {
                throw new HashMismatchException(blockFile.getName(), expected, blockFile.getPreviousHash(), "Previous");
            }
        });
    }
}
