package com.hedera.downloader;

import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.base.Stopwatch;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.databaseUtilities.ApplicationStatus;
import com.hedera.downloader.Downloader.*;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.hedera.downloader.Downloader.bucketName;
import static com.hedera.downloader.Downloader.s3Client;

@Log4j2
public class DownloaderTest {

  @Test
  public void testDownloader() {
    
  }

  @Test
  public void testSetupCloudConnection() {
    System.out.println("create a mock for ConfigLoader");
  }

  @Test
  public void testHashMapTiming() {
    Map<String,String> hashmap = new HashMap<>();
    long start = System.currentTimeMillis();
    for (int i = 0; i < 100000; i++) {
      hashmap.put(Integer.toString(i), "foo");
    }
    long end = System.currentTimeMillis();
    System.out.println(end - start);
  }

  @Test
  public void testTreeMapTiming() {
    Map<String,String> treemap = new TreeMap<>();
    long start = System.currentTimeMillis();
    for (int i = 0; i < 100000; i++) {
      treemap.put(Integer.toString(i), "foo");
    }
    long end = System.currentTimeMillis();
    System.out.println(end - start);
  }

  @Test
  public void testDownloadSigFiles() {
//    DownloadType type = DownloadType.RCD;
//    ApplicationStatus applicationStatus = new ApplicationStatus();
//    String s3Prefix = null;
//    String lastValidFileName = null;
//    try {
//      switch (type) {
//        case RCD:
//          s3Prefix = ConfigLoader.getRecordFilesS3Location();
//          lastValidFileName = applicationStatus.getLastValidDownloadedRecordFileName();
////          saveFilePath = ConfigLoader.getDownloadToDir(ConfigLoader.OPERATION_TYPE.RECORDS);
//          break;
//
//        case BALANCE:
//          s3Prefix = "accountBalances/balance";
//          lastValidFileName = applicationStatus.getLastValidDownloadedBalanceFileName();
////          saveFilePath = ConfigLoader.getDownloadToDir(ConfigLoader.OPERATION_TYPE.BALANCE);
//          break;
//
//        case EVENT:
//          s3Prefix = ConfigLoader.getEventFilesS3Location();
//          lastValidFileName = applicationStatus.getLastValidDownloadedEventFileName();
////          saveFilePath = ConfigLoader.getDownloadToDir(ConfigLoader.OPERATION_TYPE.EVENTS);
//          break;
//
//        default:
//          throw new UnsupportedOperationException("Invalid DownloadType " + type);
//      }
//      HashMap<String, List<File>> sigFilesMap = new HashMap<>();
//      // refresh node account ids
//      List<String> nodeAccountIds;
//      nodeAccountIds = loadNodeAccountIDs();
//      for (String nodeAccountId : nodeAccountIds) {
//        if (Utility.checkStopFile()) {
//          log.info("Stop file found, stopping");
//          break;
//        }
//        log.debug("Downloading {} signature files for node {} created after file {}", type, nodeAccountId, lastValidFileName);
//        // Get a list of objects in the bucket, 100 at a time
//        String prefix = s3Prefix + nodeAccountId + "/";
//        int count = 0;
//        int downloadCount = 0;
//        int downloadMax = ConfigLoader.getMaxDownloadItems();
//        Stopwatch stopwatch = Stopwatch.createStarted();
//
//        ListObjectsRequest listRequest = new ListObjectsRequest()
//                .withBucketName(bucketName)
//                .withPrefix(prefix)
//                .withDelimiter("/")
//                .withMarker(prefix + lastValidFileName)
//                .withMaxKeys(100);
//        ObjectListing objects = s3Client.listObjects(listRequest);
//
//          while(downloadCount <= downloadMax) {
//            if (Utility.checkStopFile()) {
//              log.info("Stop file found, stopping");
//              break;
//            }
//            List<S3ObjectSummary> summaries = objects.getObjectSummaries();
//            for(S3ObjectSummary summary : summaries) {
//              if (Utility.checkStopFile()) {
//                log.info("Stop file found, stopping");
//                break;
//              } else if (downloadCount >= downloadMax) {
//                break;
//              }
//
//              String s3ObjectKey = summary.getKey();
//
//              if (((isNeededSigFile(s3ObjectKey, type) && s3KeyComparator.compare(s3ObjectKey, prefix + lastValidFileName) > 0))
//                      || (lastValidFileName.isEmpty())) {
//                String saveTarget = saveFilePath + s3ObjectKey;
//                Pair<Boolean, File> result = saveToLocal(bucketName, s3ObjectKey, saveTarget);
//                if (result.getLeft()) count++;
//                if (downloadMax != 0) downloadCount++;
//
//                File sigFile = result.getRight();
//                if (sigFile != null) {
//                  String fileName = sigFile.getName();
//                  List<File> files = sigFilesMap.getOrDefault(fileName, new ArrayList<>());
//                  files.add(sigFile);
//                  sigFilesMap.put(fileName, files);
//                }
//              }
//            }
//            if (Utility.checkStopFile()) {
//              log.info("Stop file found, stopping");
//              break;
//            } else if (downloadCount >= downloadMax) {
//              break;
//            } else if (objects.isTruncated()) {
//              objects = s3Client.listNextBatchOfObjects(objects);
//            } else {
//              break;
//            }
//
//          log.info("Downloaded {} {} signatures for node {} in {}", count, type, nodeAccountId, stopwatch);
//        }
//      }
//
//      return sigFilesMap;
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
  }

  @Test
  public void testLoadNodeAccountIDs() {
      List<String> nodes = new ArrayList<>();
      try {
        byte[] addressBookBytes = Utility.getBytes(ConfigLoader.getAddressBookFile());
        if (addressBookBytes != null) {
          NodeAddressBook nodeAddressBook = NodeAddressBook.parseFrom(addressBookBytes);
          for (NodeAddress address : nodeAddressBook.getNodeAddressList()) {
            nodes.add(address.getMemo().toStringUtf8());
          }
        } else {
          log.error("Address book file {} empty or unavailable", ConfigLoader.getAddressBookFile());
        }
      } catch (IOException ex) {
        log.error("Failed to load node account IDs from {}", ConfigLoader.getAddressBookFile(), ex);
      }
      System.out.println(nodes.size());
      System.out.println(Collections.singletonList(nodes));
    }
}