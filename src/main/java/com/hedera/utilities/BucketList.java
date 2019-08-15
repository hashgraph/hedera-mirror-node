package com.hedera.utilities;

import java.util.List;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.downloader.Downloader;

public class BucketList extends Downloader {


	public BucketList() {
	}

	public static void main(String[] args) {

		setupCloudConnection();

		String s3Prefix = ""; //configLoader.getAccountBalanceS3Location();
		bucketName = ConfigLoader.getBucketName();
		
		ListObjectsRequest listRequest = new ListObjectsRequest()
				.withBucketName(bucketName)
				.withMaxKeys(100);
			
		ObjectListing objects = s3Client.listObjects(listRequest);
		try {
			List<S3ObjectSummary> summaries = objects.getObjectSummaries();
			for(S3ObjectSummary summary : summaries) {
				System.out.println(summary.getKey());
			}
			
		} catch(AmazonServiceException e) {
			// The call was transmitted successfully, but Amazon S3 couldn't process
			// it, so it returned an error response.
            log.error(MARKER, "Balance download failed, Exception: {}", e);
		} catch(SdkClientException e) {
			// Amazon S3 couldn't be contacted for a response, or the client
			// couldn't parse the response from Amazon S3.
            log.error(MARKER, "Balance download failed, Exception: {}", e);
		}

	}
}
