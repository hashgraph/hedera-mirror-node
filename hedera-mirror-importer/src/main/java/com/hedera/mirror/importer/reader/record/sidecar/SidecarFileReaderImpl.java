/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.reader.record.sidecar;

import com.hedera.mirror.common.domain.transaction.SidecarFile;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import jakarta.inject.Named;
import java.security.DigestInputStream;
import java.security.MessageDigest;

@Named
public class SidecarFileReaderImpl implements SidecarFileReader {

    @Override
    public void read(SidecarFile sidecarFile, StreamFileData streamFileData) {
        try (var digestInputStream = new DigestInputStream(
                streamFileData.getInputStream(),
                MessageDigest.getInstance(sidecarFile.getHashAlgorithm().getName()))) {
            var protoSidecarFile = com.hedera.services.stream.proto.SidecarFile.parseFrom(digestInputStream);
            var bytes = streamFileData.getBytes();
            sidecarFile.setActualHash(digestInputStream.getMessageDigest().digest());
            sidecarFile.setBytes(bytes);
            sidecarFile.setCount(protoSidecarFile.getSidecarRecordsCount());
            sidecarFile.setRecords(protoSidecarFile.getSidecarRecordsList());
            sidecarFile.setSize(bytes.length);
        } catch (InvalidStreamFileException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidStreamFileException("Error reading sidecar file " + sidecarFile.getName(), e);
        }
    }
}
