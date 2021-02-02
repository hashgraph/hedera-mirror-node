#!/usr/bin/env bash
set -ex

cd "$(dirname $0)/.."

# name of the service and directories
name=hedera-mirror-rosetta
configdir="/opt/${name}"
libdir="/opt/lib/${name}"
execname="$(ls -1 ${name}-*)"

if [[ ! -f "${execname}" ]]; then
    echo "Can't find ${execname}. Aborting"
    exit 1
fi

mkdir -p "${configdir}" "${libdir}"
systemctl stop "${name}.service" || true

if [[ ! -f "${configdir}/application.yml" ]]; then
    echo "Fresh install of ${execname}"
    read -p "Database hostname: " dbHost
    read -p "Database name: " dbName
    read -p "Rosetta user password: " dbPassword
    read -p "Database port: " dbPort
    read -p "Rosetta user: " dbUser
    read -p "Rosetta api port: " apiPort
    cat >"${configdir}/application.yml" <<EOF
hedera:
  mirror:
    rosetta:
      apiVersion: 1.4.4
      db:
        host: ${dbHost}
        name: ${dbName}
        password: ${dbPassword}
        port: ${dbPort}
        username: ${dbUser}
      online: true
      network: DEMO
      nodeVersion: 0
      port: ${apiPort}
      realm: 0
      shard: 0
      version: 0.20.0
EOF
fi

echo "Copying new binary"
rm -f "${libdir}/${name}"
cp "${execname}" "${libdir}"
ln -s "${libdir}/${execname}" "${libdir}/${name}"

echo "Setting up ${name} systemd service"
cp "scripts/${name}.service" /etc/systemd/system
systemctl daemon-reload
systemctl enable "${name}.service"

echo "Starting ${name} service"
systemctl start "${name}.service"

echo "Installation completed successfully"
