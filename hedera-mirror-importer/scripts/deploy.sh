#!/bin/sh -ex

name=hedera-mirror-importer
usretc="/usr/etc/${name}"
usrlib="/usr/lib/${name}"
varlib="/var/lib/${name}"

# CD to parent directory
cd "$(dirname $0)/.."
version=$(ls -1 -d "../"${name}-[vb]* | tr '\n' '\0' | xargs -0 -n 1 basename | tail -1 | sed -e "s/${name}-//")
if [ -z "${version}" ]; then
    echo "Can't find ${name}-v* versioned parent directory. Unrecognized layout. Aborting"
    exit 1
fi

mkdir -p "${usretc}" "${usrlib}" "${varlib}"

if [ -f "/usr/lib/mirror-node/mirror-node.jar" ] || [ -f "${usrlib}/${name}.jar" ]; then
    echo "Upgrading to ${version}"

    echo "Stopping ${name} service"
    systemctl stop "${name}.service" || true

    # Migrate from mirror-node directory structure
    if [ -f "/usr/lib/mirror-node/mirror-node.jar" ]; then
        echo "Migrating from 'mirror-node' to '${name}'"
        systemctl stop mirror-node.service || true
        systemctl disable mirror-node.service || true
        mv /usr/etc/mirror-node/* ${usretc}
        mv /usr/lib/mirror-node/* ${usrlib}
        mv /var/lib/mirror-node/* ${varlib}
        rmdir /usr/etc/mirror-node /usr/lib/mirror-node /var/lib/mirror-node
        rm /etc/systemd/system/mirror-node.service
        sed -i "s#dataPath: .*#dataPath: ${varlib}#" "${usretc}/application.yml"
    fi

    # Migrate the address book from the old location
    if [ -f "${usretc}/0.0.102" ] && [ ! -f "${varlib}/addressbook.bin" ]; then
        cp "${usretc}/0.0.102" "${varlib}/addressbook.bin"
    fi

    # Migrate from config.json to application.yml
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
rm -f "${usrlib}/${name}.jar"
cp "${name}-${version}.jar" "${usrlib}"
ln -s "${usrlib}/${name}-${version}.jar" "${usrlib}/${name}.jar"

echo "Setting up ${name} systemd service"
cp "scripts/${name}.service" /etc/systemd/system
systemctl daemon-reload
systemctl enable "${name}.service"

echo "Starting ${name} service"
systemctl start "${name}.service"

echo "Installation completed successfully"
