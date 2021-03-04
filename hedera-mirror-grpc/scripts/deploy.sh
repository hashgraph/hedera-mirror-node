#!/usr/bin/env bash
set -ex

cd "$(dirname "${0}")/.."

# name of the service and directories
name=hedera-mirror-grpc
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
    read -rp "Database hostname: " dbHost
    read -rp "Database password: " dbPassword
    cat > "${usretc}/application.yml" <<EOF
hedera:
  mirror:
    grpc:
      db:
        host:  ${dbHost}
        password:  ${dbPassword}
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
