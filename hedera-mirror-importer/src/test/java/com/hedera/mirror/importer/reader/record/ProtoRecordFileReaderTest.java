package com.hedera.mirror.importer.reader.record;

import java.io.DataOutputStream;
import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.output.NullOutputStream;

import com.hedera.mirror.common.domain.DigestAlgorithm;

public class ProtoRecordFileReaderTest extends AbstractRecordFileReaderTest {

    @Override
    protected RecordFileReader getRecordFileReader() {
        return new ProtoRecordFileReader();
    }

    @Override
    protected boolean filterFile(int version) {
        return version == 6;
    }

    // @Test
    void test() {
        try {

            var metadataStreamDigest = MessageDigest.getInstance(DigestAlgorithm.SHA384.getName());
            var dosMeta = new DataOutputStream(
                    new DigestOutputStream(NullOutputStream.NULL_OUTPUT_STREAM, metadataStreamDigest));
            dosMeta.writeInt(6);
            dosMeta.writeInt(0);
            dosMeta.writeInt(27);
            dosMeta.writeInt(1);

            byte[] startRunningHashBytes = {-112, 39, -90, 11, 48, -85, -23, -84, -97, 113, 71, 6, -84, -2, -25, 64,
                    -120, -86, 76, 21, -51, -50, 104, -92, 101, 11, -23, -69, -69, -43, 43, 28, 39, -14, 125, 33, -106
                    , 53, 118, 1, 52, 105, 115, -120, 5, 96, -72, 41};
            dosMeta.writeInt(DigestAlgorithm.SHA384.getType());
            dosMeta.writeInt(startRunningHashBytes.length);
            dosMeta.write(startRunningHashBytes);

            byte[] endRunningHashBytes = {-114, -35, -120, 101, 26, 112, 74, -118, -44, 57, 49, -113, 35, -117, -39,
                    91, -52, 17, 41, -35, 50, 35, 73, -72, -120, -99, -111, -116, -95, -79, -34, -85, 53, 17, -76, -68
                    , -78, 42, -8, -29, -115, -64, 8, -13, -59, 74, -53, -80};
            dosMeta.writeInt(DigestAlgorithm.SHA384.getType());
            dosMeta.writeInt(endRunningHashBytes.length);
            dosMeta.write(endRunningHashBytes);

            dosMeta.writeLong(-9223372036854775804L);

            dosMeta.flush();
            dosMeta.close();
            System.out.println(Hex.encodeHex(metadataStreamDigest.digest()));
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
