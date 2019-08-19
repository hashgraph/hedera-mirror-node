#!/bin/bash

# AWS Settings
export STREAM_AWS_ACCESS_KEY_ID=<redacted>
export STREAM_AWS_SECRET_ACCESS_KEY=<redacted>
export STREAM_PRIMARY_BUCKET=hedera-export-test
export STREAM_SECONDARY_BUCKET=hedera-export-test-alt

# Google Settings
export STREAM_GOOGLE_ACCESS_KEY_ID=<redacted>
export STREAM_GOOGLE_SECRET_ACCESS_KEY=<redacted>
export STREAM_GOOGLE_CLOUD_BUCKET=hedera-export-test

# File extension settings
export STREAM_ARCHIVE_LOCATION=/opt/hgcapp/eventsStreams/archive
export STREAM_FILE_EXTENSION=evts
export STREAM_SIG_EXTENSION=evts_sig


# Directory Settings
export STREAM_LOCATION=/opt/hgcapp/eventsStreams/events_0.0.3
export STREAM_NODE_INFO=0.0.3
export STREAM_UPLOAD_PATH=eventsStreams/events_0.0.3

if [[ -z "${STREAM_FILE_EXTENSION}" || -z "${STREAM_SIG_EXTENSION}" ]]; then
    echo "[ERROR] You must specify the STREAM_FILE_EXTENSION and STREAM_SIG_EXTENSION to watch for."
    exit 1
fi

if [[ -z "${STREAM_UPLOAD_PATH}" ]]; then
    echo "[ERROR] You must specify STREAM_UPLOAD_PATH as an upload path for S3 and GCS."
    exit 1
fi

if [[ -z "${STREAM_AWS_ACCESS_KEY_ID}" || -z "${STREAM_AWS_SECRET_ACCESS_KEY}" ]]; then
    echo "[ERROR] STREAM_AWS_ACCESS_KEY_ID and/or STREAM_AWS_SECRET_ACCESS_KEY are not set."
    exit 1
fi

if [[ -z "${STREAM_GOOGLE_ACCESS_KEY_ID}" || -z "${STREAM_GOOGLE_SECRET_ACCESS_KEY}" ]]; then
    echo "[ERROR] STREAM_GOOGLE_APPLICATION_CREDENTIALS are not set."
    exit 1
fi

if ! type "mc" > /dev/null; then
    echo "[ERROR] mc binary is not installed"
    exit 1
fi

if [ ! -d "${STREAM_LOCATION}" ]; then
    echo "[ERROR] STREAM_LOCATION: $STREAM_LOCATION does not exist or is not readable."
    exit 1
fi

if [ ! -d "${STREAM_ARCHIVE_LOCATION}" ]; then
    echo "[ERROR] STREAM_ARCHIVE_LOCATION: $STREAM_ARCHIVE_LOCATION does not exist or is not readable."
    exit 1
fi

echo "[INFO] STREAM_LOCATION=$STREAM_LOCATION"
echo "[INFO] STREAM_ARCHIVE_LOCATION=$STREAM_ARCHIVE_LOCATION"

mc config host add amazons3 https://s3.amazonaws.com $STREAM_AWS_ACCESS_KEY_ID $STREAM_AWS_SECRET_ACCESS_KEY
mc config host add gcs https://storage.googleapis.com $STREAM_GOOGLE_ACCESS_KEY_ID $STREAM_GOOGLE_SECRET_ACCESS_KEY

SIG_LOCATION=${STREAM_LOCATION}/*.${STREAM_SIG_EXTENSION}
while :
do
    for sigfile in $SIG_LOCATION
    do
        filename=$(basename -- "$sigfile")
        filename="${filename%.*}"
        recordfile="${STREAM_LOCATION}/${filename}.${STREAM_FILE_EXTENSION}"

        if [ -f $recordfile ]; then
            mc cp --quiet $recordfile amazons3/$STREAM_PRIMARY_BUCKET/$STREAM_UPLOAD_PATH/
            r1=$?
            mc cp --quiet $sigfile amazons3/$STREAM_PRIMARY_BUCKET/$STREAM_UPLOAD_PATH/
            r2=$?
            mc cp --quiet $recordfile gcs/$STREAM_GOOGLE_CLOUD_BUCKET/$STREAM_UPLOAD_PATH/
            r3=$?
            mc cp --quiet $sigfile gcs/$STREAM_GOOGLE_CLOUD_BUCKET/$STREAM_UPLOAD_PATH/
            r4=$?

            if [[ $r1 -eq 0 && $r2 -eq 0 && $r3 -eq 0 && $r4 -eq 0 ]]; then
                mv $recordfile $STREAM_ARCHIVE_LOCATION
                mv $sigfile $STREAM_ARCHIVE_LOCATION
                echo "[INFO] upload complete for: $recordfile"
            else
                echo "[ERROR] one or more uploads failed for file: $recordfile."
            fi
        else
            if [[ $sigfile != $SIG_LOCATION ]]; then
                echo "[ERROR] recordfile does not exist for signature $sigfile"
            else
                echo "[INFO] nothing to do"
                # only sleep if there is nothing to do, otherwise keep processing.
                sleep 1
            fi
        fi
    done