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

    # Handle the upgrade from config.json database params to application.yml database params
    appyml="${usretc}/application.yml"
    if [ -f "${usretc}/config.json" ] && [ ! -f  "${appyml}" ]; then
        echo -e "hedera:\n  mirror:\n    db:" > "${appyml}"
        cat "${usretc}/config.json" | awk -F: '{gsub(/ |\"/, "", $0); gsub(/,$/, "", $0); print}' | grep -E "^(api|db)" | \
          sed -e "s/apiUsername:/api-username: /" -e "s/apiPassword:/api-password: /" -e "s/dbName:/name: /" \
            -e "s/dbUsername:/username: /" -e "s/dbPassword:/password: /" | grep -v dbUrl | \
            sed -e "s/^/      /" >> "${appyml}"
        dbUrl=$(grep "dbUrl" "${usretc}/config.json" | sed -e "s#.*//##" -e "s#/.*##")
        dbhost=$(echo ${dbUrl} | sed -e "s#:.*##")
        dbport=$(echo ${dbUrl} | grep ':' | sed -e "s#.*:##")
        if [ -n "${dbhost}" ]; then
            echo "      host: ${dbhost}" >> "${appyml}"
        fi
        if [ -n "${dbport}" ]; then
            echo "      port: ${dbport}" >> "${appyml}"
        fi
        sed -ie '/apiPassword/d;/apiUsername/d;/dbName/d;/dbUsername/d;/dbPassword/d;/dbUrl/d' "${usretc}/config.json"
    fi
else
    echo "Fresh install of ${version}"
    echo "Copying config (will need to be edited)"
    cp -n config/* "${usretc}"
fi

echo "Copying new binary"
cp mirror-node.jar "${usrlib}"

echo "Setting up mirror-node systemd service"
cp scripts/mirror-node.service /etc/systemd/system
systemctl daemon-reload
systemctl enable mirror-node.service

echo "Starting mirror-node service"
systemctl start mirror-node.service
