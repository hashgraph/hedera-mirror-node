package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

public class AddressBookRepositoryTest extends AbstractRepositoryTest {
    private final EntityId addressBookEntityId101 = EntityId.of("0.0.101", EntityTypeEnum.FILE);
    private final EntityId addressBookEntityId102 = EntityId.of("0.0.102", EntityTypeEnum.FILE);

    @Test
    void save() {
        AddressBook addressBook = addressBookRepository.save(addressBook(null));
        assertThat(addressBookRepository.findById(addressBook.getConsensusTimestamp()))
                .get()
                .isEqualTo(addressBook);
    }

    @Test
    void findLatestAddressBook() {
        addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId102)
                .startConsensusTimestamp(1L).consensusTimestamp(2L)));
        addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId101)
                .startConsensusTimestamp(3L).consensusTimestamp(4L)));
        AddressBook addressBook = addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId102)
                .startConsensusTimestamp(5L).consensusTimestamp(6L)));
        assertThat(addressBookRepository
                .findTopByFileIdAndIsCompleteIsTrueOrderByConsensusTimestampDesc(addressBookEntityId102))
                .get()
                .isNotNull()
                .isEqualTo(addressBook);
    }

    @Test
    void findAddressBooks() {
        AddressBook addressBook1 = addressBookRepository.save(addressBook(ab -> ab.startConsensusTimestamp(1L)
                .consensusTimestamp(2L)));
        AddressBook addressBook2 = addressBookRepository
                .save(addressBook(ab -> ab.operationType(AddressBook.FileOperation.UPDATE).startConsensusTimestamp(3L)
                        .consensusTimestamp(4L)));
        AddressBook addressBook3 = addressBookRepository
                .save(addressBook(ab -> ab.operationType(AddressBook.FileOperation.APPEND).startConsensusTimestamp(5L)
                        .consensusTimestamp(6L)));
        addressBookRepository
                .save(addressBook(ab -> ab.isComplete(false).operationType(AddressBook.FileOperation.UPDATE)
                        .startConsensusTimestamp(7L).consensusTimestamp(8L).endConsensusTimestamp(null)));
        addressBookRepository
                .save(addressBook(ab -> ab.isComplete(false).operationType(AddressBook.FileOperation.APPEND)
                        .startConsensusTimestamp(9L).consensusTimestamp(10L).endConsensusTimestamp(null)));
        AddressBook addressBook4 = addressBookRepository
                .save(addressBook(ab -> ab.operationType(AddressBook.FileOperation.APPEND).startConsensusTimestamp(11L)
                        .consensusTimestamp(12L)));
        AddressBook addressBook5 = addressBookRepository
                .save(addressBook(ab -> ab.operationType(AddressBook.FileOperation.UPDATE).startConsensusTimestamp(13L)
                        .consensusTimestamp(14L)));

        assertThat(addressBookRepository
                .findCompleteAddressBooks(addressBook4.getConsensusTimestamp(), addressBookEntityId102))
                .isNotNull()
                .containsSequence(addressBook1, addressBook2, addressBook3, addressBook4)
                .doesNotContain(addressBook5);
    }

    private AddressBook addressBook(Consumer<AddressBook.AddressBookBuilder> addressBookCustomizer) {
        Long now = Instant.now().getEpochSecond();
        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .consensusTimestamp(now + 1)
                .startConsensusTimestamp(now)
                .endConsensusTimestamp(now + 2)
                .fileData("address book memo".getBytes())
                .isComplete(true)
                .nodeCount(12)
                .operationType(AddressBook.FileOperation.CREATE)
                .fileId(addressBookEntityId102);

        if (addressBookCustomizer != null) {
            addressBookCustomizer.accept(builder);
        }

        return builder.build();
    }
}
