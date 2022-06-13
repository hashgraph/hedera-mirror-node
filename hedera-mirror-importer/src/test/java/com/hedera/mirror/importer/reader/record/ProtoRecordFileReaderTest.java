package com.hedera.mirror.importer.reader.record;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.primitives.Ints;
import java.io.IOException;
import java.io.InputStream;
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
    void testCorrectFailureWhenVersionIsWrong() throws IOException {
        var inputStreamMock = mock(InputStream.class);
        when(inputStreamMock.readNBytes(4)).thenReturn(Ints.toByteArray(5));
        var streamFileDataMock = mock(StreamFileData.class);
        when(streamFileDataMock.getInputStream()).thenReturn(inputStreamMock);
        final InvalidStreamFileException invalidStreamFileException = assertThrows(InvalidStreamFileException.class,
                () -> new ProtoRecordFileReader().read(streamFileDataMock));
        assertEquals("Expected file with version 6, given 5.", invalidStreamFileException.getMessage());
    }
}
