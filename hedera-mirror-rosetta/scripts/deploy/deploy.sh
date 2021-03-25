#!/usr/bin/env bash
set -ex

cd "$(dirname "${0}")/.."

# name of the service and directories
name=hedera-mirror-rosetta
binary="/usr/bin/${name}"
configdir="/usr/etc/${name}"
configfile="${configdir}/application.yml"
execname="$(ls -1 ${name}*)"

if [[ ! -f "${execname}" ]]; then
    echo "Can't find ${execname}. Aborting"
    exit 1
fi

mkdir -p "${configdir}"
systemctl stop "${name}.service" || true

if [[ ! -f "${configfile}" ]]; then
   echo "Fresh install of ${name}"
    read -rp "Database hostname: " dbHost
    read -rp "Database name: " dbName
    read -rp "Rosetta user password: " dbPassword
    read -rp "Database port: " dbPort
    read -rp "Rosetta user: " dbUser
    cat > "${configfile}" <<EOF
hedera:
  mirror:
    rosetta:
      db:
        host: ${dbHost}
        name: ${dbName}
        password: ${dbPassword}
        port: ${dbPort}
        username: ${dbUser}
EOF
fi

echo "Copying new binary"
cp "${execname}" "${binary}"

echo "Setting up ${name} systemd service"
cp "scripts/${name}.service" /etc/systemd/system
systemctl daemon-reload
systemctl enable "${name}.service"

echo "Starting ${name} service"
systemctl start "${name}.service"

echo "Installation completed successfully"

