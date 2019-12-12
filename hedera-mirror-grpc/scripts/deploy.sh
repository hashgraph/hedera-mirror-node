#!/bin/sh -ex

artifactname=hedera-mirror-grpc-hcs

# name of the service and directories
name=hedera-mirror-grpc
usretc="/usr/etc/${name}"
usrlib="/usr/lib/${name}"

# CD to parent directory
cd "$(dirname $0)/.."
version=$(ls -1 -d "../"${artifactname}-[vb]* | tr '\n' '\0' | xargs -0 -n 1 basename | tail -1 | sed -e "s/${artifactname}-//")
if [ -z "${version}" ]; then
    echo "Can't find ${artifactname}-[vb]* versioned parent directory. Unrecognized layout. Aborting"
    exit 1
fi
jarname="${artifactname}-${version:1}.jar"
if [ ! -f "${jarname}" ]; then
    echo "Can't find ${jarname}. Aborting"
    exit 1
fi

mkdir -p "${usretc}" "${usrlib}"
systemctl stop "${name}.service" || true

if [ ! -f "${usretc}/application.yml" ]; then
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
