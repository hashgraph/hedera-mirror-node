/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.hapi.node.state.file;

import static com.hedera.pbj.runtime.ProtoTestTools.BOOLEAN_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.BYTES_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.STRING_TESTS_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.state.token.AbstractStateTest;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class FileTest extends AbstractStateTest {

    @SuppressWarnings("EqualsWithItself")
    @Test
    void testTestEqualsAndHashCode() {
        if (ARGUMENTS.size() >= 3) {
            final var item1 = ARGUMENTS.get(0);
            final var item2 = ARGUMENTS.get(1);
            final var item3 = ARGUMENTS.get(2);
            assertEquals(item1, item1);
            assertEquals(item2, item2);
            assertEquals(item3, item3);
            assertNotEquals(item1, item2);
            assertNotEquals(item2, item3);
            final var item1HashCode = item1.hashCode();
            final var item2HashCode = item2.hashCode();
            final var item3HashCode = item3.hashCode();
            assertNotEquals(item1HashCode, item2HashCode);
            assertNotEquals(item2HashCode, item3HashCode);
        }
    }

    @Test
    void testEqualsWithNull() {
        final var item1 = ARGUMENTS.get(0);
        assertNotEquals(null, item1);
    }

    @Test
    void testEqualsWithDifferentClass() {
        final var item1 = ARGUMENTS.get(0);
        assertNotEquals(item1, new Object());
    }

    @Test
    void testEqualsWithNullFileId() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullFileId =
                item1.copyBuilder().fileId((FileID) null).build();
        assertNotEquals(item1, item1WithNullFileId);
    }

    @Test
    void testEqualsWithDifferentContents() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithDifferentContents =
                item1.copyBuilder().contents(Bytes.wrap(new byte[] {1, 2, 3})).build();
        assertNotEquals(item1, item1WithDifferentContents);
    }

    @Test
    void testEqualsWithDifferentMemo() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithDifferentMemo =
                item1.copyBuilder().memo("Different Memo").build();
        assertNotEquals(item1, item1WithDifferentMemo);
    }

    @Test
    void testEqualsWithDifferentExpirationTime() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithDifferentExpirationTime =
                item1.copyBuilder().expirationSecond(123456789L).build();
        assertNotEquals(item1, item1WithDifferentExpirationTime);
    }

    @Test
    void testEqualsWithNullKeys() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithNullKeys = item1.copyBuilder().keys((KeyList) null).build();
        assertNotEquals(item1, item1WithNullKeys);
    }

    @Test
    void testEqualsWithDifferentDeletedStatus() {
        final var item1 = ARGUMENTS.get(0);
        final var item1WithDifferentDeletedStatus =
                item1.copyBuilder().deleted(false).build();
        assertNotEquals(item1, item1WithDifferentDeletedStatus);
    }

    @Test
    void testFileIdBuilder() {
        final var initialFile = File.newBuilder().build();

        final var fileId =
                FileID.newBuilder().shardNum(0).realmNum(0).fileNum(1234).build();
        final var updatedFile = initialFile
                .copyBuilder()
                .fileId(FileID.newBuilder().shardNum(0).realmNum(0).fileNum(1234))
                .build();

        assertThat(updatedFile.fileId()).isEqualTo(fileId);
    }

    @Test
    void testExpirationSecondBuilder() {
        final var initialFile = File.newBuilder().build();

        final var expirationSecond = 987654321L;
        final var updatedFile =
                initialFile.copyBuilder().expirationSecond(expirationSecond).build();

        assertThat(updatedFile.expirationSecondSupplier().get()).isEqualTo(expirationSecond);
    }

    @Test
    void testExpirationSecondSupplierBuilder() {
        final var initialFile = File.newBuilder().build();

        final var expirationSecond = 987654321L;
        final var updatedFile = initialFile
                .copyBuilder()
                .expirationSecond(() -> expirationSecond)
                .build();

        assertThat(updatedFile.expirationSecondSupplier().get()).isEqualTo(expirationSecond);
    }

    @Test
    void testKeysBuilder() {
        final var initialFile = File.newBuilder().build();

        final var keyList = KeyList.newBuilder().build();
        final var updatedFile =
                initialFile.copyBuilder().keys(KeyList.newBuilder()).build();

        assertThat(updatedFile.keys()).isEqualTo(keyList);
    }

    @Test
    void testContentsBuilder() {
        final var initialFile = File.newBuilder().build();

        final var contents = new byte[] {1, 2, 3, 4};
        final var updatedFile =
                initialFile.copyBuilder().contents(Bytes.wrap(contents)).build();

        assertThat(updatedFile.contents()).isEqualTo(Bytes.wrap(contents));
    }

    @Test
    void testMemoBuilder() {
        final var initialFile = File.newBuilder().build();

        final var memo = "Test memo";
        final var updatedFile = initialFile.copyBuilder().memo(memo).build();

        assertThat(updatedFile.memo()).isEqualTo(memo);
    }

    @Test
    void testDeletedBuilder() {
        final var initialFile = File.newBuilder().build();

        final var updatedFile = initialFile.copyBuilder().deleted(true).build();

        assertThat(updatedFile.deleted()).isTrue();
    }

    @Test
    void testPreSystemDeleteExpirationSecondBuilder() {
        final var initialFile = File.newBuilder().build();

        final var preSystemDeleteExpirationSecond = 123456789L;
        final var updatedFile = initialFile
                .copyBuilder()
                .preSystemDeleteExpirationSecond(preSystemDeleteExpirationSecond)
                .build();

        assertThat(updatedFile.preSystemDeleteExpirationSecond()).isEqualTo(preSystemDeleteExpirationSecond);
    }

    @Test
    void testHasFileId() {
        final var item1 = ARGUMENTS.get(0);
        final var item1HasFileIdFalse =
                item1.copyBuilder().fileId((FileID) null).build();

        assertThat(item1.hasFileId()).isTrue();
        assertThat(item1HasFileIdFalse.hasFileId()).isFalse();
    }

    @Test
    void testFileIdOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var item1NoFileId = item1.copyBuilder().fileId((FileID) null).build();
        final var defaultFileId =
                FileID.newBuilder().shardNum(0).realmNum(0).fileNum(9999).build();

        assertThat(item1.fileIdOrElse(defaultFileId)).isEqualTo(item1.fileId());
        assertThat(item1NoFileId.fileIdOrElse(defaultFileId)).isEqualTo(defaultFileId);
    }

    @Test
    void testFileIdOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var item1NoFileId = item1.copyBuilder().fileId((FileID) null).build();
        assertThat(item1.fileIdOrThrow()).isNotNull();
        assertThatThrownBy(item1NoFileId::fileIdOrThrow)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Field fileId is null");
    }

    @Test
    void testIfFileId() {
        final var item1 = ARGUMENTS.get(0);
        final var item1NoFileId = item1.copyBuilder().fileId((FileID) null).build();
        List<FileID> listToAcceptFileIds = new ArrayList<>();
        item1.ifFileId(listToAcceptFileIds::add);
        item1NoFileId.ifFileId(listToAcceptFileIds::add);
        assertThat(listToAcceptFileIds).isNotEmpty().hasSize(1);
    }

    @Test
    void testHasKeys() {
        final var item1 = ARGUMENTS.get(0);
        final var item1NoKeys = item1.copyBuilder().keys((KeyList) null).build();

        assertThat(item1.hasKeys()).isTrue();
        assertThat(item1NoKeys.hasKeys()).isFalse();
    }

    @Test
    void testKeysOrElse() {
        final var item1 = ARGUMENTS.get(0);
        final var itemEmptyKeys = item1.copyBuilder().keys((KeyList) null).build();
        assertThat(item1.keysOrElse(KeyList.DEFAULT)).isEqualTo(item1.keys());
        assertThat(itemEmptyKeys.keysOrElse(KeyList.DEFAULT)).isEqualTo(KeyList.DEFAULT);
    }

    @Test
    void testKeysOrThrow() {
        final var item1 = ARGUMENTS.get(0);
        final var item1NoKeys = item1.copyBuilder().keys((KeyList) null).build();
        assertThat(item1.keysOrThrow()).isNotNull();
        assertThatThrownBy(item1NoKeys::keysOrThrow)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Field keys is null");
    }

    @Test
    void testIfKeys() {
        final var item1 = ARGUMENTS.get(0);
        final var item1NoKeys = item1.copyBuilder().keys((KeyList) null).build();
        List<KeyList> listToAcceptKeys = new ArrayList<>();
        item1.ifKeys(listToAcceptKeys::add);
        item1NoKeys.ifKeys(listToAcceptKeys::add);
        assertThat(listToAcceptKeys).isNotEmpty().hasSize(1);
    }

    /**
     * List of all valid arguments for testing, built as a static list, so we can reuse it.
     */
    public static final List<File> ARGUMENTS;

    static {
        final var fileIdList = FILE_IDS_ARGUMENTS;
        final var expirationSecondList = LONG_TESTS_LIST;
        final var keysList = KEY_LISTS_ARGUMENTS;
        final var contentsList = BYTES_TESTS_LIST;
        final var memoList = STRING_TESTS_LIST;
        final var deletedList = BOOLEAN_TESTS_LIST;
        final var preSystemDeleteExpirationSecondList = LONG_TESTS_LIST;

        // work out the longest of all the lists of args as that is how many test cases we need
        final int maxValues = IntStream.of(
                        fileIdList.size(),
                        expirationSecondList.size(),
                        keysList.size(),
                        contentsList.size(),
                        memoList.size(),
                        deletedList.size(),
                        preSystemDeleteExpirationSecondList.size())
                .max()
                .getAsInt();
        // create new stream of model objects using lists above as constructor params
        ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                .mapToObj(i -> new File(
                        fileIdList.get(Math.min(i, fileIdList.size() - 1)),
                        expirationSecondList.get(Math.min(i, expirationSecondList.size() - 1)),
                        keysList.get(Math.min(i, keysList.size() - 1)),
                        contentsList.get(Math.min(i, contentsList.size() - 1)),
                        memoList.get(Math.min(i, memoList.size() - 1)),
                        deletedList.get(Math.min(i, deletedList.size() - 1)),
                        preSystemDeleteExpirationSecondList.get(
                                Math.min(i, preSystemDeleteExpirationSecondList.size() - 1))))
                .toList();
    }
}
