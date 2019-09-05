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

if [ -d "${usrlib}" ]; then
    upgrade=1
    echo "Upgrading to ${version}"

    echo "Stopping services"
    sudo touch "${usrlib}/stop"
    sleep 5

    sudo systemctl stop mirror-balance-downloader.service
    sudo systemctl stop mirror-balance-parser.service
    sudo systemctl stop mirror-record-downloader.service
    sudo systemctl stop mirror-record-parser.service

    echo "Backing up binaries"
    sudo mv "${usrlib}/lib" "${usrlib}/lib.${ts}.old"
    sudo mv "${usrlib}/mirror-node.jar" "${usrlib}/mirror-node.jar.${ts}.old"
else
    echo "Fresh install of ${version}"
fi

echo "Copying binaries"
sudo mkdir -p /usr/etc/mirror-node "${usrlib}" /var/lib/mirror-node
sudo cp -R lib/ mirror-node.jar "${usrlib}"

if [ ${upgrade} -eq 0 ]; then
    echo "Copying config (will need to be edited)"
    cp -n config/* /usr/etc/mirror-node/
else
    echo "Removing last version of binaries"
    sudo rm -rf "${usrlib}/lib.${ts}.old" "${usrlib}/mirror-node.jar.${ts}.old"
fi

echo "Setting up systemd services"
sudo cp systemd/*mirror*.service /etc/systemd/system
sudo systemctl daemon-reload
sudo systemctl enable mirror-balance-downloader.service
sudo systemctl enable mirror-balance-parser.service
sudo systemctl enable mirror-record-downloader.service
sudo systemctl enable mirror-record-parser.service

echo "Starting services"
sudo rm -f "${usrlib}/stop"
sudo systemctl start mirror-balance-downloader.service
sudo systemctl start mirror-balance-parser.service
sudo systemctl start mirror-record-downloader.service
sudo systemctl start mirror-record-parser.service
