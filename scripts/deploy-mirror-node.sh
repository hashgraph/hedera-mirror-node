#!/bin/sh -ex

# CD to parent directory containing scripts, lib, mirror-node.jar, etc.
cd "$(dirname $0)/.."
version=$(ls -1 -d "../"mirror-node-[vb]* | tr '\n' '\0' | xargs -0 -n 1 basename | tail -1 | sed -e "s/mirror-node-//")
if [ -z "${version}" ]; then
    echo "Can't find mirror-node-v* versioned parent directory. Unrecognized layout. Aborting"
    exit 1
fi

usrlib=/usr/lib/mirror-node/
usretc=/usr/etc/mirror-node/
ts=$(date -u +%s)
upgrade=0

mkdir -p "${usretc}" "${usrlib}" /var/lib/mirror-node

if [ -f "${usrlib}/mirror-node.jar" ]; then
    upgrade=1
    echo "Upgrading to ${version}"

    # Stop the service
    echo "Stopping mirror-node service"
    systemctl stop mirror-node.service || true

    echo "Backing up binary"
    mv "${usrlib}/mirror-node.jar" "${usrlib}/mirror-node.jar.${ts}.old"

    # Handle the migration from config.json to application.yml
    if [ -f "${usretc}/config.json" ] && [ ! -f  "${usretc}/application.yml" ]; then
        apiPassword=$(grep -oP '"apiPassword": "\K[^"]+' "${usretc}/config.json")
        bucketName=$(grep -oP '"bucketName": "\K[^"]+' "${usretc}/config.json")
        dbHost=$(grep -oP '"dbUrl": "jdbc:postgresql://\K[^:]+' "${usretc}/config.json")
        dbPassword=$(grep -oP '"dbPassword": "\K[^"]+' "${usretc}/config.json")
        downloadToDir=$(grep -oP '"downloadToDir": "\K[^"]+' "${usretc}/config.json")
        cat > "${usretc}/application.yml" <EOF
hedera:
  mirror:
    dataPath: ${downloadToDir}
    db:
      apiPassword: ${apiPassword}
      host: ${dbHost}
      password: ${dbPassword}
    downloader:
      bucketName: ${bucketName}
EOF
    fi
else
    echo "Fresh install of ${version}"
    echo "Creating empty config (will need to be edited)"
    cat > "${usretc}/application.yml" <EOF
hedera:
  mirror:
    dataPath: /var/lib/mirror-node
    db:
      apiPassword:
      host:
      password:
    downloader:
      bucketName:
EOF
fi

echo "Copying new binary"
cp mirror-node.jar "${usrlib}"

echo "Setting up mirror-node systemd service"
cp scripts/mirror-node.service /etc/systemd/system
systemctl daemon-reload
systemctl enable mirror-node.service

echo "Starting mirror-node service"
systemctl start mirror-node.service
