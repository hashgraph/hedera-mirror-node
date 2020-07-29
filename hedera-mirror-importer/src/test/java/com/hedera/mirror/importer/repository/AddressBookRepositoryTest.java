package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

public class AddressBookRepositoryTest extends AbstractRepositoryTest {

    @Resource
    protected AddressBookRepository addressBookRepository;

    private final EntityId addressBookEntityId101 = EntityId.of("0.0.101", EntityTypeEnum.FILE);
    private final EntityId addressBookEntityId102 = EntityId.of("0.0.102", EntityTypeEnum.FILE);

    @Test
    void save() {
        AddressBook addressBook = addressBookRepository.save(addressBook(null, 4, 4));
        addressBooksMatch(addressBook, addressBookRepository.findById(addressBook.getConsensusTimestamp())
                .get());
    }

    @Test
    void findLatestAddressBook() {
        addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId102), 2, 2));
        addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId101), 4, 4));
        AddressBook addressBook = addressBookRepository
                .save(addressBook(ab -> ab.fileId(addressBookEntityId102), 6, 6));
        assertThat(addressBookRepository
                .findLatestAddressBook(7L, addressBookEntityId102.getId()))
                .get()
                .isNotNull()
                .extracting(AddressBook::getConsensusTimestamp)
                .isEqualTo(6L);
    }

    @Test
    void findAddressBooks() {
        AddressBook addressBook1 = addressBookRepository.save(addressBook(null, 2, 2));
        AddressBook addressBook2 = addressBookRepository
                .save(addressBook(null, 4, 4));
        AddressBook addressBook3 = addressBookRepository
                .save(addressBook(null, 6, 6));
        addressBookRepository
                .save(addressBook(ab -> ab.fileId(addressBookEntityId101), 8, 8));
        addressBookRepository
                .save(addressBook(ab -> ab.fileId(addressBookEntityId101), 10, 10));
        AddressBook addressBook4 = addressBookRepository
                .save(addressBook(null, 12, 12));
        AddressBook addressBook5 = addressBookRepository
                .save(addressBook(null, 14, 14));

        assertThat(addressBookRepository
                .findLatestAddressBooks(addressBook4.getConsensusTimestamp(), addressBookEntityId102))
                .isNotNull()
                .extracting(AddressBook::getConsensusTimestamp)
                .containsSequence(2L, 4L, 6L, 12L)
                .doesNotContain(14L);
    }

    private AddressBook addressBook(Consumer<AddressBook.AddressBookBuilder> addressBookCustomizer,
                                    long consensusTimestamp, int nodeCount) {
        List<AddressBookEntry> addressBookEntryList = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            long id = i;
            long nodeId = 3 + i;
            addressBookEntryList
                    .add(addressBookEntry(a -> a.consensusTimestamp(consensusTimestamp).memo("0.0." + nodeId)
                            .nodeId(nodeId).nodeAccountId(EntityId.of("0.0.5", EntityTypeEnum.ACCOUNT))));
        }

        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .consensusTimestamp(consensusTimestamp)
                .startConsensusTimestamp(consensusTimestamp + 1)
                .fileData("address book memo".getBytes())
                .nodeCount(nodeCount)
                .fileId(addressBookEntityId102)
                .addressBookEntries(addressBookEntryList);

        if (addressBookCustomizer != null) {
            addressBookCustomizer.accept(builder);
        }

        return builder.build();
    }

    private AddressBookEntry addressBookEntry(Consumer<AddressBookEntry.AddressBookEntryBuilder> nodeAddressCustomizer) {
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(Instant.now().getEpochSecond())
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

    private void addressBooksMatch(AddressBook expected, AddressBook actual) {
        assertAll(
                () -> assertNotNull(actual),
                () -> assertArrayEquals(expected.getFileData(), actual.getFileData()),
                () -> assertEquals(expected.getConsensusTimestamp(), actual.getConsensusTimestamp()),
                () -> assertEquals(expected.getNodeCount(), actual.getNodeCount()),
                () -> assertEquals(expected.getAddressBookEntries().size(), actual.getAddressBookEntries().size())
        );
    }
}
