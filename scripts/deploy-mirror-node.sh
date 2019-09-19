#!/bin/sh -ex

# CD to parent directory containing scripts, lib, mirror-node.jar, etc.
cd "$(dirname $0)/.."
version=$(ls -1 -d "../"mirror-node-[vb]* | tr '\n' '\0' | xargs -0 -n 1 basename | tail -1 | sed -e "s/mirror-node-//")
if [ -z "${version}" ]; then
    echo "Can't find mirror-node-v* versioned parent directory. Unrecognized layout. Aborting"
    exit 1
fi

usrlib=/usr/lib/mirror-node/
ts=$(date -u +%s)
upgrade=0

sudo mkdir -p /usr/etc/mirror-node "${usrlib}" /var/lib/mirror-node

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
else
    echo "Fresh install of ${version}"
    echo "Copying config (will need to be edited)"
    cp -n config/* /usr/etc/mirror-node/
fi

echo "Copying binaries"
sudo cp mirror-node.jar "${usrlib}"

echo "Setting up systemd services"
sudo cp scripts/*mirror*.service /etc/systemd/system
sudo systemctl daemon-reload
sudo systemctl enable mirror-node.service

echo "Starting services"
sudo rm -f "${usrlib}/stop"
sudo systemctl start mirror-node.service
