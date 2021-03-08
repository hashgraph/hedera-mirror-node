#!/usr/bin/env bash
set -ex

cd "$(dirname "${0}")/.."

# name of the service and directories
name=hedera-mirror-importer
usretc="/usr/etc/${name}"
usrlib="/usr/lib/${name}"
jarname="$(ls -1 ${name}-*.jar)"

if [[ ! -f "${jarname}" ]]; then
    echo "Can't find ${jarname}. Aborting"
    exit 1
fi

mkdir -p "${usretc}" "${usrlib}"
systemctl stop "${name}.service" || true

if [[ ! -f "${usretc}/application.yml" ]]; then
    echo "Fresh install of ${name}"
    read -rp "Bucket name: " bucketName
    read -rp "Hedera network: " network
    read -rp "Database hostname: " dbHost
    read -rp "Database password: " dbPassword
    read -rp "REST user database password: " restPassword
    cat > "${usretc}/application.yml" <<EOF
hedera:
  mirror:
    importer:
      db:
        restPassword: ${restPassword}
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
