package com.hedera.mirror.importer.reader.record;

public class ProtoRecordFileReaderTest extends AbstractRecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        return new ProtoRecordFileReader();
    }

    @Override
    protected boolean filterFile(int version) {
        return version == 6;
    }
}
