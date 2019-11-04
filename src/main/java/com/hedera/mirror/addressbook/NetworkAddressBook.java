package com.hedera.mirror.addressbook;

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import com.google.protobuf.InvalidProtocolBufferException;

import com.hedera.mirror.MirrorProperties;
import com.hedera.mirror.domain.HederaNetwork;
import com.hedera.mirror.domain.NodeAddress;
import com.hedera.utilities.Utility;

import com.hederahashgraph.api.proto.java.NodeAddressBook;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.inject.Named;

@Log4j2
@Named
public class NetworkAddressBook {

    final private Path addressBookPath;
    private byte[] addressBookBytes;
    private Map<String, PublicKey> nodeIDPubKeyMap;

    public NetworkAddressBook(MirrorProperties mirrorProperties) {
        // Hot-loading changes to network type (mainnet to testnet or vice-versa) or address book path are not
        // desirable, so reference to mirrorProperties is not kept around.
        addressBookPath = mirrorProperties.getAddressBookPath();
        loadDefaultIfNeeded(mirrorProperties.getNetwork());
        try {
            addressBookBytes = Files.readAllBytes(addressBookPath);
            parseAddressBook(addressBookBytes);
        } catch (IOException e) {
            // when mirror node is starting and loading the address book fails, it is best to stop immediately.
            throw new RuntimeException("Error initializing address book from " + addressBookPath, e);
        }
    }

    private void loadDefaultIfNeeded(HederaNetwork hederaNetwork) {
        try {
            File addressBookFile = addressBookPath.toFile();
            if (!addressBookFile.exists() || !addressBookFile.canRead()) {
                Resource resource = new ClassPathResource(String.format("addressbook/%s", hederaNetwork.name().toLowerCase()));
                Path defaultAddressBook = resource.getFile().toPath();
                Utility.ensureDirectory(addressBookPath.getParent());
                Files.copy(defaultAddressBook, addressBookPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied default address book {} to {}", resource, addressBookPath);
            }
        } catch (Exception e) {
            log.error("Unable to copy {} address book to {}", hederaNetwork, addressBookPath, e);
        }
    }

    public synchronized Map<String, PublicKey> getNodeIDPubKeyMap() {
        return nodeIDPubKeyMap;
    }

    public synchronized void update(byte[] newAddressBookBytes) throws IOException {
        log.info("Updating address book");
        updateInternal(newAddressBookBytes);
    }

    public synchronized void append(byte[] extraContents) throws IOException {
        log.info("Appending to address book");
        byte[] newAddressBookBytes = Arrays.copyOf(addressBookBytes, addressBookBytes.length + extraContents.length);
        System.arraycopy(extraContents, 0, newAddressBookBytes, addressBookBytes.length, extraContents.length);
        updateInternal(newAddressBookBytes);
    }

    private void updateInternal(byte[] newAddressBookBytes) throws IOException {
        try {
            // first parse to validate new contents
            parseAddressBook(newAddressBookBytes);
            addressBookBytes = newAddressBookBytes;
            Files.write(addressBookPath, addressBookBytes);
            log.info("New address book successfully saved to {}", addressBookPath);
        } catch(Exception e) {
            throw new IOException("Failed updating address book", e);
        }
    }

    private void parseAddressBook(byte[] contentBytes) throws IllegalArgumentException {
        ImmutableList.Builder<NodeAddress> builder = ImmutableList.builder();
        try {
            NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(contentBytes);
            for (com.hederahashgraph.api.proto.java.NodeAddress nodeAddressProto : nodeAddressBook.getNodeAddressList()) {
                NodeAddress nodeAddress = NodeAddress.builder()
                        .id(nodeAddressProto.getMemo().toStringUtf8())
                        .ip(nodeAddressProto.getIpAddress().toStringUtf8())
                        .port(nodeAddressProto.getPortno())
                        .publicKey(nodeAddressProto.getRSAPubKey())
                        .build();
                log.info(nodeAddress);
                builder.add(nodeAddress);
            }
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to parse address book", e);
            throw new IllegalArgumentException("Failed to parse address book");
        }
        nodeIDPubKeyMap = builder.build().stream()
                .collect(Collectors.toMap(NodeAddress::getId, NodeAddress::getPublicKeyAsObject));
    }
}
