package com.hedera.utilities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.downloader.Downloader;
import com.hedera.signatureVerifier.NodeSignatureVerifier;
import com.hedera.utilities.Utility;

public class BucketList extends Downloader {


	public BucketList(ConfigLoader configLoader) {
		super(configLoader);
	}

	public static void main(String[] args) {

		configLoader = new ConfigLoader();

		setupCloudConnection();

		String s3Prefix = ""; //configLoader.getAccountBalanceS3Location();
		bucketName = configLoader.getBucketName();
		
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
