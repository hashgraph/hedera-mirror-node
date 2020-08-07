package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.Scheduled;

import com.hedera.mirror.importer.exception.DuplicateFileException;
import com.hedera.mirror.importer.parser.FilePoller;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.util.ShutdownHelper;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@ConditionalOnRecordParser
@AllArgsConstructor
public class RecordFilePoller implements FilePoller {

    private final RecordParserProperties parserProperties;
    private final RecordFileParser recordFileParser;

    @Override
    @Scheduled(fixedRateString = "${hedera.mirror.importer.parser.record.frequency:100}")
    public void poll() {
        if (ShutdownHelper.isStopping()) {
            return;
        }
        Path path = parserProperties.getValidPath();
        log.debug("Parsing record files from {}", path);
        try {
            File file = path.toFile();
            if (file.isDirectory()) {

                String[] files = file.list();
                Arrays.sort(files);           // sorted by name (timestamp)

                // add directory prefix to get full path
                List<String> fullPaths = Arrays.asList(files).stream()
                        .map(s -> file + "/" + s)
                        .collect(Collectors.toList());

                if (fullPaths != null && fullPaths.size() != 0) {
                    log.trace("Processing record files: {}", fullPaths);
                    loadRecordFiles(fullPaths);
                } else {
                    log.debug("No files to parse");
                }
            } else {
                log.error("Input parameter is not a folder: {}", path);
            }
        } catch (Exception e) {
            log.error("Error parsing files", e);
        }
    }

    /**
     * read and parse a list of record files
     *
     * @throws Exception
     */
    private void loadRecordFiles(List<String> filePaths) {
        Collections.sort(filePaths);
        for (String filePath : filePaths) {
            if (ShutdownHelper.isStopping()) {
                return;
            }

            File file = new File(filePath);

            try (InputStream fileInputStream = new FileInputStream(file)) {
                recordFileParser.parse(new StreamFileData(filePath, fileInputStream));

                if (parserProperties.isKeepFiles()) {
                    Utility.archiveFile(file, parserProperties.getParsedPath());
                } else {
                    FileUtils.deleteQuietly(file);
                }
            } catch (FileNotFoundException e) {
                log.warn("File does not exist {}", filePath);
                return;
            } catch (Exception e) {
                log.error("Error parsing file {}", filePath, e);
                if (!(e instanceof DuplicateFileException)) { // if DuplicateFileException, continue with other
                    // files
                    return;
                }
            }
        }
    }
}
