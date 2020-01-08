#!/usr/bin/env bash
set -ex

cd "$(dirname $0)/.."

# name of the service and directories
name=hedera-mirror-importer
usretc="/usr/etc/${name}"
usrlib="/usr/lib/${name}"
varlib="/var/lib/${name}"
jarname="$(ls -1 ${name}-*.jar)"

if [[ ! -f "${jarname}" ]]; then
    echo "Can't find ${jarname}. Aborting"
    exit 1
fi

mkdir -p "${usretc}" "${usrlib}" "${varlib}"
systemctl stop "${name}.service" || true

if [[ -f "/usr/lib/mirror-node/mirror-node.jar" || -f "${usrlib}/${name}.jar" ]]; then
    echo "Upgrading to ${jarname}"

    # Migrate from mirror-node directory structure
    if [[ -f "/usr/lib/mirror-node/mirror-node.jar" ]]; then
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
fi

# One time removal of address book since it changed from 10 to 13 nodes. Remove in next release
rm -f "${varlib}/addressbook.bin"

if [[ ! -f "${usretc}/application.yml" ]]; then
    echo "Fresh install of ${jarname}"
    read -p "Bucket name: " bucketName
    read -p "Hedera network: " network
    read -p "Database hostname: " dbHost
    read -p "Database password: " dbPassword
    read -p "API user database password: " apiPassword
    cat > "${usretc}/application.yml" <<EOF
hedera:
  mirror:
    dataPath: ${varlib}
    db:
      apiPassword: ${apiPassword}
      host: ${dbHost}
      password: ${dbPassword}
    downloader:
      bucketName: ${bucketName}
    network: ${network}
EOF
fi

echo "Copying new binary"
rm -f "${usrlib}/${name}.jar"
cp "${jarname}" "${usrlib}"
ln -s "${usrlib}/${jarname}" "${usrlib}/${name}.jar"

echo "Setting up ${name} systemd service"
cp "scripts/${name}.service" /etc/systemd/system
systemctl daemon-reload
systemctl enable "${name}.service"

echo "Starting ${name} service"
systemctl start "${name}.service"

echo "Installation completed successfully"
