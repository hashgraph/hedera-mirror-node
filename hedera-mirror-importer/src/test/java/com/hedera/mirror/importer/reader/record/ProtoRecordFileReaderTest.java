package com.hedera.mirror.importer.reader.record;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.primitives.Ints;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;

public class ProtoRecordFileReaderTest extends AbstractRecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        return new ProtoRecordFileReader();
    }

    @Override
    protected boolean filterFile(int version) {
        return version == 6;
    }

    @Test
    void testCorrectFailureWhenVersionIsWrong() {
        var streamFileDataMock = mock(StreamFileData.class);
        when(streamFileDataMock.getInputStream()).thenReturn(new ByteArrayInputStream(Ints.toByteArray(5)));
        final InvalidStreamFileException invalidStreamFileException = assertThrows(InvalidStreamFileException.class,
                () -> new ProtoRecordFileReader().read(streamFileDataMock));
        assertEquals("Expected file with version 6, given 5.", invalidStreamFileException.getMessage());
    }
}
