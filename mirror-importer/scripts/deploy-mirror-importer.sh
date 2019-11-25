#!/bin/sh -ex

# CD to parent directory containing scripts, lib, mirror-importer.jar, etc.
cd "$(dirname $0)/.."
version=$(ls -1 -d "../"mirror-importer-[vb]* | tr '\n' '\0' | xargs -0 -n 1 basename | tail -1 | sed -e "s/mirror-importer-//")
if [ -z "${version}" ]; then
    echo "Can't find mirror-importer-v* versioned parent directory. Unrecognized layout. Aborting"
    exit 1
fi

usretc=/usr/etc/mirror-importer
usrlib=/usr/lib/mirror-importer
varlib=/var/lib/mirror-importer
ts=$(date -u +%s)

mkdir -p "${usretc}" "${usrlib}" "${varlib}"

if [ -f "/usr/lib/mirror-node/mirror-node.jar" ] || [ -f "${usrlib}/mirror-importer.jar" ]; then
    echo "Upgrading to ${version}"

    # Stop the service
    echo "Stopping ${oldjarname} service"
    systemctl stop ${oldjarname}.service || true

    if [ -f "/usr/lib/mirror-node/mirror-node.jar" ]; then
        echo "Migrating from 'mirror-node' to 'mirror-importer'"
        oldjarname="mirror-node"
        mv /usr/etc/mirror-node/* ${usretc}
        mv /usr/lib/mirror-node/* ${usrlib}
        mv /var/lib/mirror-node/* ${varlib}
    else
        oldjarname="mirror-importer"
    fi

    echo "Backing up binary"
    mv "${usrlib}/${oldjarname}.jar" "${usrlib}/${oldjarname}.jar.${ts}.old"

    if [ -f "${usretc}/0.0.102" ] && [ ! -f "${varlib}/addressbook.bin" ]; then
      cp "${usretc}/0.0.102" "${varlib}/addressbook.bin"
    fi

    # Handle the migration from config.json to application.yml
    if [ -f "${usretc}/config.json" ] && [ ! -f  "${usretc}/application.yml" ]; then
        network="MAINNET"
        if (grep "bucketName.*testnet" "${usretc}/config.json"); then
            network="TESTNET"
        fi

        apiPassword=$(grep -oP '"apiPassword": "\K[^"]+' "${usretc}/config.json")
        bucketName=$(grep -oP '"bucketName": "\K[^"]+' "${usretc}/config.json")
        dbHost=$(grep -oP '"dbUrl": "jdbc:postgresql://\K[^:]+' "${usretc}/config.json")
        dbPassword=$(grep -oP '"dbPassword": "\K[^"]+' "${usretc}/config.json")
        downloadToDir=$(grep -oP '"downloadToDir": "\K[^"]+' "${usretc}/config.json")
        cat > "${usretc}/application.yml" <<EOF
hedera:
  mirror:
    dataPath: ${downloadToDir}
    db:
      apiPassword: ${apiPassword}
      host: ${dbHost}
      password: ${dbPassword}
    downloader:
      bucketName: ${bucketName}
    network: ${network}
EOF
    fi
else
    echo "Fresh install of ${version}"
    echo "Creating empty config (will need to be edited)"
    cat > "${usretc}/application.yml" <<EOF
hedera:
  mirror:
    dataPath: ${varlib}
    db:
      apiPassword:
      host:
      password:
    downloader:
      bucketName:
EOF
fi

echo "Copying new binary"
cp mirror-importer.jar "${usrlib}"

echo "Setting up mirror-importer systemd service"
cp scripts/mirror-importer.service /etc/systemd/system
systemctl daemon-reload
systemctl enable mirror-importer.service

echo "Starting mirror-importer service"
systemctl start mirror-importer.service
