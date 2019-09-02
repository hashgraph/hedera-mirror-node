package com.hedera.filedelimiters;

public class FileDelimiter {
	public static final String HASH_ALGORITHM = "SHA-384";
	public static final byte EVENT_TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
	public static final byte EVENT_STREAM_FILE_VERSION_LEGACY = 2;
	public static final byte EVENT_STREAM_VERSION = 2;
	public static final byte EVENT_STREAM_FILE_VERSION_CURRENT = 3;
	public static final byte EVENT_STREAM_START_NO_TRANS_WITH_VERSION = 0x5b;
	public static final byte EVENT_STREAM_START_WITH_VERSION = 0x5a;
	public static final byte EVENT_COMM_EVENT_LAST = 0x46;
	
	public static final byte RECORD_TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
	public static final int RECORD_FORMAT_VERSION = 2;
	public static final byte RECORD_TYPE_RECORD = 2;          // next data type is transaction and its record
	public static final byte RECORD_TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed
	
	public static final byte SIGNATURE_TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed
	public static final byte SIGNATURE_TYPE_FILE_HASH = 4;       // next 48 bytes are hash384 of content of corresponding RecordFile
}
