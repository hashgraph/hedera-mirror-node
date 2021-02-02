#!/usr/bin/env bash
set -ex

cd "$(dirname $0)/.."

# name of the service and directories
name=hedera-mirror-rosetta
usretc="/opt/${name}"
usrlib="/opt/lib/${name}"
execname="$(ls -1 ${name}-*)"

if [[ ! -f "${execname}" ]]; then
    echo "Can't find ${execname}. Aborting"
    exit 1
fi

mkdir -p "${usretc}" "${usrlib}"
systemctl stop "${name}.service" || true

if [[ ! -f "${usretc}/application.yml" ]]; then
    echo "Fresh install of ${execname}"
    read -p "Database hostname: " dbHost
    read -p "Database name: " dbName
    read -p "Rosetta user password: " dbPassword
    read -p "Database port: " dbPort
    read -p "Rosetta user: " dbUser
    cat >"${usretc}/application.yml" <<EOF
hedera:
  mirror:
    importer:
      db:
        host: ${dbHost}
        name: ${dbName}
        password: ${dbPassword}
        port: ${dbPort}
        user: ${dbUser}
EOF
fi

echo "Copying new binary"
rm -f "${usrlib}/${name}"
cp "${execname}" "${usrlib}"
ln -s "${usrlib}/${execname}" "${usrlib}/${name}"

echo "Setting up ${name} systemd service"
cp "scripts/${name}.service" /etc/systemd/system
systemctl daemon-reload
systemctl enable "${name}.service"

echo "Starting ${name} service"
systemctl start "${name}.service"

echo "Installation completed successfully"
