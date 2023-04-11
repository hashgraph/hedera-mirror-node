package com.hedera.mirror.importer.downloader;

import lombok.Value;

@Value
public class PathParameterProperties {

    Long pathExpirationTimestampMap;

    CommonDownloaderProperties.PathType pathType;
}
