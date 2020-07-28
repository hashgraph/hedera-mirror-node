package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.function.Consumer;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

public class AddressBookEntryRepositoryTest extends AbstractRepositoryTest {

    private final EntityId addressBookEntityId102 = EntityId.of("0.0.102", EntityTypeEnum.FILE);

    @Resource
    protected AddressBookEntryRepository addressBookEntryRepository;

    @Resource
    protected AddressBookRepository addressBookRepository;

    @Test
    void save() {
        addressBookRepository.save(addressBook(null));
        AddressBookEntry addressBookEntry = addressBookEntryRepository.save(addressBookEntry(null));
        assertThat(addressBookEntryRepository.findById(addressBookEntry.getId()))
                .get()
                .isEqualTo(addressBookEntry);
    }

    @Test
    void verifySequence() {
        addressBookRepository.save(addressBook(null));
        addressBookEntryRepository.save(addressBookEntry(null));
        addressBookEntryRepository.save(addressBookEntry(null));
        addressBookEntryRepository.save(addressBookEntry(null));
        assertThat(addressBookEntryRepository.findAll())
                .isNotNull()
                .extracting(AddressBookEntry::getId)
                .containsSequence(1L, 2L, 3L);
    }

    @Test
    void retrieveNodeAddressesForGivenAddressBook() {
        Long addressBook1ConsensusTimeStamp = Instant.now().getEpochSecond();

        addressBookRepository.save(addressBook(ab -> ab.consensusTimestamp(addressBook1ConsensusTimeStamp)));
        addressBookEntryRepository.save(addressBookEntry(na -> na.consensusTimestamp(addressBook1ConsensusTimeStamp)));

        Long addressBook2ConsensusTimeStamp = addressBook1ConsensusTimeStamp + 1;
        addressBookRepository.save(addressBook(ab -> ab.consensusTimestamp(addressBook2ConsensusTimeStamp)));
        AddressBookEntry addressBookEntry1 = addressBookEntryRepository
                .save(addressBookEntry(na -> na.consensusTimestamp(addressBook2ConsensusTimeStamp)));
        AddressBookEntry addressBookEntry2 = addressBookEntryRepository
                .save(addressBookEntry(na -> na.consensusTimestamp(addressBook2ConsensusTimeStamp)));

        Long addressBook3ConsensusTimeStamp = addressBook2ConsensusTimeStamp + 2;
        addressBookRepository.save(addressBook(ab -> ab.consensusTimestamp(addressBook3ConsensusTimeStamp)));
        addressBookEntryRepository.save(addressBookEntry(na -> na.consensusTimestamp(addressBook3ConsensusTimeStamp)));
        addressBookEntryRepository.save(addressBookEntry(na -> na.consensusTimestamp(addressBook3ConsensusTimeStamp)));
        assertThat(addressBookEntryRepository
                .findAddressBookEntriesByConsensusTimestamp(addressBookEntry1.getConsensusTimestamp()))
                .isNotNull()
                .containsSequence(addressBookEntry1, addressBookEntry2);
    }

    private AddressBookEntry addressBookEntry(Consumer<AddressBookEntry.AddressBookEntryBuilder> nodeAddressCustomizer) {
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(0L)
                .ip("127.0.0.1")
                .publicKey("rsa+public/key")
                .memo("0.0.3")
                .nodeAccountId(EntityId.of("0.0.5", EntityTypeEnum.ACCOUNT))
                .nodeId(5)
                .nodeCertHash("nodeCertHash".getBytes());

        if (nodeAddressCustomizer != null) {
            nodeAddressCustomizer.accept(builder);
        }

        return builder.build();
    }

    private AddressBook addressBook(Consumer<AddressBook.AddressBookBuilder> addressBookCustomizer) {

        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .consensusTimestamp(0L)
                .fileData("address book memo".getBytes())
                .fileId(addressBookEntityId102);

        if (addressBookCustomizer != null) {
            addressBookCustomizer.accept(builder);
        }

        return builder.build();
    }
}
