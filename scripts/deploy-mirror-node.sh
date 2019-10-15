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

sudo mkdir -p "${usretc}" "${usrlib}" /var/lib/mirror-node

if [ -f "${usrlib}/mirror-node.jar" ]; then
    upgrade=1
    echo "Upgrading to ${version}"

    echo "Stopping services"
    sudo touch "${usrlib}/stop"
    sleep 5

    # Optionally stop these since some of these might not exist
    sudo systemctl stop mirror-balance-downloader.service || true
    sudo systemctl stop mirror-balance-parser.service || true
    sudo systemctl stop mirror-record-downloader.service || true
    sudo systemctl stop mirror-record-parser.service || true
    sudo systemctl stop mirror-node.service || true
    sudo rm -f /etc/systemd/systemd/mirror-*.service

    echo "Backing up binaries"
    sudo rm -rf "${usrlib}/lib"* "${usrlib}/logs" "${usrlib}/mirror-node.jar."*
    sudo mv "${usrlib}/mirror-node.jar" "${usrlib}/mirror-node.jar.${ts}.old"

    # Handle the upgrade from config.json database params to application.yml database params
    appyml="${usretc}/application.yml"
    if [ -f "${usretc}/config.json" ] && [ ! -f  "${appyml}" ]; then
        sudo echo -e "hedera:\n  mirror:\n    db:" > "${appyml}"
        sudo cat "${usretc}/config.json" | awk -F: '{gsub(/ |\"/, "", $0); gsub(/,$/, "", $0); print}' | grep -E "^(api|db)" | \
          sed -e "s/apiUsername:/api-username: /" -e "s/apiPassword:/api-password: /" -e "s/dbName:/name: /" \
            -e "s/dbUsername:/username: /" -e "s/dbPassword:/password: /" | grep -v dbUrl | \
            sudo sed -e "s/^/      /" >> "${appyml}"
        dbUrl=$(sudo grep "dbUrl" "${usretc}/config.json" | sed -e "s#.*//##" -e "s#/.*##")
        dbhost=$(echo ${dbUrl} | sed -e "s#:.*##")
        dbport=$(echo ${dbUrl} | grep ':' | sed -e "s#.*:##")
        if [ -n "${dbhost}" ]; then
            sudo echo "      host: ${dbhost}" >> "${appyml}"
        fi
        if [ -n "${dbport}" ]; then
            sudo echo "      port: ${dbport}" >> "${appyml}"
        fi
        sudo sed -ie '/apiPassword/d;/apiUsername/d;/dbName/d;/dbUsername/d;/dbPassword/d;/dbUrl/d' "${usretc}/config.json"
    fi
else
    echo "Fresh install of ${version}"
    echo "Copying config (will need to be edited)"
    cp -n config/* "#{usretc}"
fi

echo "Copying binaries"
sudo cp mirror-node.jar "${usrlib}"

echo "Setting up systemd services"
sudo cp scripts/*mirror*.service /etc/systemd/system
sudo systemctl daemon-reload
sudo systemctl enable mirror-node.service

echo "Starting services"
sudo systemctl start mirror-node.service
