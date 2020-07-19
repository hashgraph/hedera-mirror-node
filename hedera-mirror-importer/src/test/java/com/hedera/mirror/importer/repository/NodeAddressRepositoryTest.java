package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.NodeAddress;

public class NodeAddressRepositoryTest extends AbstractRepositoryTest {

    @Test
    void save() {
        NodeAddress nodeAddress = nodeAddressRepository.save(nodeAddress(null));
        assertThat(nodeAddressRepository.findById(nodeAddress.getId()))
                .get()
                .isEqualTo(nodeAddress);
    }

    @Test
    void verifySequence() {
        nodeAddressRepository.save(nodeAddress(null));
        nodeAddressRepository.save(nodeAddress(null));
        nodeAddressRepository.save(nodeAddress(null));
        assertThat(nodeAddressRepository.findAll())
                .isNotNull()
                .extracting(NodeAddress::getId)
                .containsSequence(1L, 2L, 3L);
    }

    @Test
    void retrieveNodeAddressesForGivenAddressBook() {
        Long addressBook1ConsensusTimeStamp = Instant.now().getEpochSecond();
        nodeAddressRepository.save(nodeAddress(na -> na.consensusTimestamp(addressBook1ConsensusTimeStamp)));
        Long addressBook2ConsensusTimeStamp = addressBook1ConsensusTimeStamp + 1;
        NodeAddress nodeAddress1 = nodeAddressRepository
                .save(nodeAddress(na -> na.consensusTimestamp(addressBook2ConsensusTimeStamp)));
        NodeAddress nodeAddress2 = nodeAddressRepository
                .save(nodeAddress(na -> na.consensusTimestamp(addressBook2ConsensusTimeStamp)));
        Long addressBook3ConsensusTimeStamp = addressBook2ConsensusTimeStamp + 2;
        nodeAddressRepository.save(nodeAddress(na -> na.consensusTimestamp(addressBook3ConsensusTimeStamp)));
        nodeAddressRepository.save(nodeAddress(na -> na.consensusTimestamp(addressBook3ConsensusTimeStamp)));
        assertThat(nodeAddressRepository.findNodeAddressesByConsensusTimestamp(nodeAddress1.getConsensusTimestamp()))
                .isNotNull()
                .containsSequence(nodeAddress1, nodeAddress2);
    }

    private NodeAddress nodeAddress(Consumer<NodeAddress.NodeAddressBuilder> nodeAddressCustomizer) {
        NodeAddress.NodeAddressBuilder builder = NodeAddress.builder()
                .consensusTimestamp(Instant.now().getEpochSecond())
                .ip("127.0.0.1")
                .publicKey("rsa+public/key")
                .memo("0.0.3")
                .nodeAccountId(EntityId.of("0.0.102", EntityTypeEnum.ACCOUNT))
                .nodeId(102)
                .nodeCertHash("nodeCertHash".getBytes());

        if (nodeAddressCustomizer != null) {
            nodeAddressCustomizer.accept(builder);
        }

        return builder.build();
    }
}
