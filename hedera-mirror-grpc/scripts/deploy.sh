#!/usr/bin/env bash
set -ex

cd "$(dirname $0)/.."

# name of the service and directories
name=hedera-mirror-grpc
monitor_name=hedera-mirror-grpc-monitor
usretc="/usr/etc/${name}"
usrlib="/usr/lib/${name}"
monitor_usretc="/usr/etc/${monitor_name}"
monitor_usrlib="/usr/lib/${monitor_name}"
jarname="$(ls -1 ${name}-*.jar)"

if [[ ! -f "${jarname}" ]]; then
    echo "Can't find ${jarname}. Aborting"
    exit 1
fi

mkdir -p "${usretc}" "${usrlib}"
mkdir -p "${monitor_usretc}" "${monitor_usrlib}"
systemctl stop "${name}.service" || true
systemctl stop "${monitor_name}.service" || true

if [[ ! -f "${usretc}/application.yml" ]]; then
    echo "Fresh install of ${version}"
    read -p "Database hostname: " dbHost
    read -p "Database password: " dbPassword
    cat > "${usretc}/application.yml" <<EOF
hedera:
  mirror:
    grpc:
      db:
        host:  ${dbHost}
        password:  ${dbPassword}
EOF
fi

if [[ ! -f "${monitor_usretc}/config" ]]; then
    echo "Fresh install of monitor ${version}"
    if [ -z "${dbHost}" ]; then
        read -p "Database hostname: " dbHost
    fi
    if [ -z "${dbPassword}" ]; then
        read -p "Database password: " dbPassword
    fi
    cat > "${monitor_usretc}/config" <<EOF
PGPASSWORD=${dbPassword}
DBHOST=${dbHost}
EOF
fi

echo "Copying new binary"
rm -f "${usrlib}/${name}.jar"
cp "${jarname}" "${usrlib}"
ln -s "${usrlib}/${jarname}" "${usrlib}/${name}.jar"

echo "Copying new monitor script"
cp -f "scripts/monitor.sh" "${monitor_usrlib}"

echo "Setting up ${name} systemd service"
cp "scripts/${name}.service" /etc/systemd/system
cp "scripts/${monitor_name}.service" /etc/systemd/system
systemctl daemon-reload
systemctl enable "${name}.service"
systemctl enable "${monitor_name}.service"

echo "Starting ${name} service"
systemctl start "${name}.service"
echo "Starting ${monitor_name} service"
systemctl start "${monitor_name}.service"

echo "Installation completed successfully"
