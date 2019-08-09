package com.hedera;

import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountID;

import org.apache.commons.lang3.tuple.Triple;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

public class UtilityTest {

	@Test
	public void stringToAccountIDTest() {

		AccountID accountID = AccountID.newBuilder().setAccountNum(100).build();
		String string = Utility.accountIDToString(accountID);
		AccountID parsed = Utility.stringToAccountID(string);
		Assert.assertTrue(Utility.isSameAccountID(accountID, parsed));
	}

	@Test
	public void parseS3SummaryKey_Test() {
		String name = "record0.0.101/2019-06-05T20:29:32.856974Z.rcd_sig";
		Triple<String, String, String> triple = Utility.parseS3SummaryKey(name);
		Assert.assertEquals("2019-06-05T20:29:32.856974Z", triple.getMiddle());
		Assert.assertEquals("0.0.101", triple.getLeft());
		Assert.assertEquals("rcd_sig", triple.getRight());
	}

	@Test
	public void parseS3SummaryKey_Test2() {
		String name = "directory/record10.10.101/2019-06-05T20:29:32.856974Z.rcd";
		Triple<String, String, String> triple = Utility.parseS3SummaryKey(name);
		Assert.assertEquals("2019-06-05T20:29:32.856974Z", triple.getMiddle());
		Assert.assertEquals("10.10.101", triple.getLeft());
		Assert.assertEquals("rcd", triple.getRight());
	}

	@Test
	public void getFileNameFromS3SummaryKey_Test() {
		String name = "record0.0.101/2019-06-05T20:29:32.856974Z.rcd_sig";
		Assert.assertEquals("2019-06-05T20:29:32.856974Z.rcd_sig", Utility.getFileNameFromS3SummaryKey(name));
	}

	@Test
	public void getInstantFromFileName_Test() {
		String name = "2019-06-05T20:29:32.856974Z.rcd_sig";
		Assert.assertEquals(Instant.parse("2019-06-05T20:29:32.856974Z"), Utility.getInstantFromFileName(name));
	}

	@Test
	public void getInstantFromEventStreamFileName_Test() {
		String name = "2019-07-25T19_57_21.217420Z.evts_sig";
		Assert.assertEquals(Instant.parse("2019-07-25T19:57:21.217420Z"), Utility.getInstantFromFileName(name));
	}


	@Test
	public void getTripleFromS3SummaryKey_Event_Test() {
		String name = "eventstreams/events_0.0.3/2019-07-25T19_57_21.217420Z.evts_sig";
		Triple<String, String, String> triple = Utility.parseS3SummaryKey(name);
		Assert.assertEquals("0.0.3", triple.getLeft());
		Assert.assertEquals("2019-07-25T19_57_21.217420Z", triple.getMiddle());
		Assert.assertEquals("evts_sig", triple.getRight());
	}
}
