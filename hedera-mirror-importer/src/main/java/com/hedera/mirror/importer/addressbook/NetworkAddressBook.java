package com.hedera.mirror.importer.addressbook;

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

import com.google.common.collect.ImmutableList;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.HederaNetwork;
import com.hedera.mirror.importer.domain.NodeAddress;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class NetworkAddressBook {

    private final MirrorProperties mirrorProperties;
    private volatile Collection<NodeAddress> nodeAddresses;

    public NetworkAddressBook(MirrorProperties mirrorProperties) {
        this.mirrorProperties = mirrorProperties;
        nodeAddresses = Collections.emptyList();
        init();
    }

    public static boolean isAddressBook(EntityId entityId) {
        return entityId != null && entityId.getType() == EntityTypeEnum.FILE.getId()
                && entityId.getEntityNum() == 102 && entityId.getShardNum() == 0 && entityId.getRealmNum() == 0;
    }

    public void updateFrom(TransactionBody transactionBody) {
        try {
            if (transactionBody.hasFileAppend()) {
                append(transactionBody.getFileAppend().getContents().toByteArray());
            } else if (transactionBody.hasFileUpdate()) {
                update(transactionBody.getFileUpdate().getContents().toByteArray());
            }
        } catch (IOException e) {
            throw new ParserException("Error appending to network address book", e);
        }
    }

    public Collection<NodeAddress> getAddresses() {
        return nodeAddresses;
    }

    private void init() {
        Path path = mirrorProperties.getAddressBookPath();
        try {
            File addressBookFile = path.toFile();

            if (!addressBookFile.canRead()) {
                if (addressBookFile.exists()) {
                    log.warn("Backing up unreadable address book: {}", path);
                    Files.move(path, path.resolveSibling(path + ".unreadable"));
                }

                byte[] addressBookBytes = null;
                Path initialAddressBook = mirrorProperties.getInitialAddressBook();

                if (initialAddressBook != null) {
                    addressBookBytes = Files.readAllBytes(initialAddressBook);
                    log.info("Loading bootstrap address book from {}", initialAddressBook);
                } else {
                    HederaNetwork hederaNetwork = mirrorProperties.getNetwork();
                    String resourcePath = String.format("/addressbook/%s", hederaNetwork.name().toLowerCase());
                    Resource resource = new ClassPathResource(resourcePath, getClass());
                    addressBookBytes = IOUtils.toByteArray(resource.getInputStream());
                    log.info("Loading bootstrap address book from {}", resource);
                }

                Utility.ensureDirectory(path.getParent());
                update(addressBookBytes);
            } else {
                log.info("Restoring existing address book {}", path);
                update(Files.readAllBytes(path));
            }
        } catch (Exception e) {
            log.error("Unable to copy address book from {} to {}", mirrorProperties.getNetwork(), path, e);
        }

        if (getAddresses().isEmpty()) {
            throw new IllegalStateException("Unable to load a valid address book");
        }
    }

    public void update(byte[] newContents) throws IOException {
        saveToDisk(newContents, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void append(byte[] extraContents) throws IOException {
        saveToDisk(extraContents, StandardOpenOption.APPEND);
    }

    private void saveToDisk(byte[] contents, OpenOption openOption) throws IOException {
        if (contents == null || contents.length == 0) {
            log.warn("Ignored empty byte array");
            return;
        }

        Path path = mirrorProperties.getAddressBookPath();
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tempPath, contents, StandardOpenOption.CREATE, StandardOpenOption.WRITE, openOption);
        log.info("Saved {}B partial address book update to {}", contents.length, tempPath);

        try {
            Collection<NodeAddress> nodeAddresses = parse(tempPath);
            if (!nodeAddresses.isEmpty()) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
                this.nodeAddresses = nodeAddresses;
                log.info("New address book with {} addresses successfully parsed and saved to {}",
                        nodeAddresses.size(), path);
            }
        } catch (Exception e) {
            log.warn("Unable to parse address book: {}", e.getMessage());
        }
    }

    private Collection<NodeAddress> parse(Path path) throws Exception {
        byte[] addressBookBytes = Files.readAllBytes(path);
        NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);
        ImmutableList.Builder<NodeAddress> builder = ImmutableList.builder();

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

        return builder.build();
    }
}
