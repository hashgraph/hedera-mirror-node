package com.hedera.mirror.importer.addressbook;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.google.common.collect.ImmutableList;
import com.hederahashgraph.api.proto.java.NodeAddressBook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import javax.inject.Named;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.HederaNetwork;
import com.hedera.mirror.importer.domain.NodeAddress;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class NetworkAddressBook {

    private MirrorProperties mirrorProperties;
    private byte[] addressBookBytes = new byte[0];

    public NetworkAddressBook(MirrorProperties mirrorProperties) {
        this.mirrorProperties = mirrorProperties;
        init();
    }

    private void init() {
        Path path = mirrorProperties.getAddressBookPath();
        try {
            File addressBookFile = path.toFile();
            if (!addressBookFile.exists() || !addressBookFile.canRead()) {
                HederaNetwork hederaNetwork = mirrorProperties.getNetwork();
                String resourcePath = String.format("/addressbook/%s", hederaNetwork.name().toLowerCase());
                Resource resource = new ClassPathResource(resourcePath, getClass());
                Utility.ensureDirectory(path.getParent());
                IOUtils.copy(resource.getInputStream(), new FileOutputStream(addressBookFile));
                log.info("Copied default address book {} to {}", resource, path);
            }
        } catch (Exception e) {
            log.error("Unable to copy address book from {} to {}", mirrorProperties.getNetwork(), path, e);
        }
    }

    public void update(byte[] newContents) throws IOException {
        addressBookBytes = newContents;
        saveToDisk();
    }

    public void append(byte[] extraContents) throws IOException {
        byte[] newAddressBook = Arrays.copyOf(addressBookBytes, addressBookBytes.length + extraContents.length);
        System.arraycopy(extraContents, 0, newAddressBook, addressBookBytes.length, extraContents.length);
        addressBookBytes = newAddressBook;
        saveToDisk();
    }

    private void saveToDisk() throws IOException {
        Path path = mirrorProperties.getAddressBookPath();
        Files.write(path, addressBookBytes);
        log.info("New address book successfully saved to {}", path);
    }

    public Collection<NodeAddress> load() {
        ImmutableList.Builder<NodeAddress> builder = ImmutableList.builder();
        Path path = mirrorProperties.getAddressBookPath();

        try {
            byte[] addressBookBytes = Files.readAllBytes(path);
            NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);

            for (com.hederahashgraph.api.proto.java.NodeAddress nodeAddressProto : nodeAddressBook
                    .getNodeAddressList()) {
                NodeAddress nodeAddress = NodeAddress.builder()
                        .id(nodeAddressProto.getMemo().toStringUtf8())
                        .ip(nodeAddressProto.getIpAddress().toStringUtf8())
                        .port(nodeAddressProto.getPortno())
                        .publicKey(nodeAddressProto.getRSAPubKey())
                        .build();
                builder.add(nodeAddress);
            }
        } catch (Exception ex) {
            log.error("Failed to parse NodeAddressBook from {}", path, ex);
        }

        return builder.build();
    }
}
