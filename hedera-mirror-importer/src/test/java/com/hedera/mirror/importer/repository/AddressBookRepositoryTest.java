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
        AddressBook addressBook = addressBookRepository.save(addressBook(null, 4));
        addressBooksMatch(addressBook, addressBookRepository.findById(addressBook.getConsensusTimestamp())
                .get());
    }

    @Test
    void findLatestAddressBook() {
        addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId102).consensusTimestamp(2L), 2));
        addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId101).consensusTimestamp(4L), 4));
        AddressBook addressBook = addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId102)
                .consensusTimestamp(6L), 6));
        assertThat(addressBookRepository
                .findTopByFileIdOrderByConsensusTimestampDesc(addressBookEntityId102))
                .get()
                .isNotNull()
                .isEqualTo(addressBook);
    }

    @Test
    void findAddressBooks() {
        AddressBook addressBook1 = addressBookRepository.save(addressBook(ab -> ab.consensusTimestamp(2L), 2));
        AddressBook addressBook2 = addressBookRepository
                .save(addressBook(ab -> ab.consensusTimestamp(4L), 4));
        AddressBook addressBook3 = addressBookRepository
                .save(addressBook(ab -> ab.consensusTimestamp(6L), 6));
        addressBookRepository
                .save(addressBook(ab -> ab.consensusTimestamp(8L).endConsensusTimestamp(null), 8));
        addressBookRepository
                .save(addressBook(ab -> ab.consensusTimestamp(10L).endConsensusTimestamp(null), 10));
        AddressBook addressBook4 = addressBookRepository
                .save(addressBook(ab -> ab.consensusTimestamp(12L), 12));
        AddressBook addressBook5 = addressBookRepository
                .save(addressBook(ab -> ab.consensusTimestamp(14L), 14));

        assertThat(addressBookRepository
                .findCompleteAddressBooks(addressBook4.getConsensusTimestamp(), addressBookEntityId102))
                .isNotNull()
                .containsSequence(addressBook1, addressBook2, addressBook3, addressBook4)
                .doesNotContain(addressBook5);
    }

    private AddressBook addressBook(Consumer<AddressBook.AddressBookBuilder> addressBookCustomizer, int nodeCount) {
        Long now = Instant.now().getEpochSecond();

        List<AddressBookEntry> addressBookEntryList = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            addressBookEntryList.add(addressBookEntry(a -> a.consensusTimestamp(now)));
        }

        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .consensusTimestamp(now)
                .endConsensusTimestamp(now + 2)
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
                .nodeAccountId(EntityId.of("0.0.102", EntityTypeEnum.ACCOUNT))
                .nodeId(102)
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
