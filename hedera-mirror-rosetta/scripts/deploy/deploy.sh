#!/usr/bin/env bash
set -ex

cd "$(dirname $0)/.."

# name of the service and directories
name=hedera-mirror-rosetta
configfile=application.yml
configdir="/usr/etc/${name}"
libdir="/usr/lib/${name}"
execname="$(ls -1 ${name}-*)"

if [[ ! -f "${execname}" ]]; then
    echo "Can't find ${execname}. Aborting"
    exit 1
fi

mkdir -p "${configdir}" "${libdir}"
systemctl stop "${name}.service" || true

if [[ ! -f "${configdir}/${configfile}" ]]; then
    if [[ ! -f "${configfile}" ]]; then
        echo "Can't find ${configfile}. Aborting"
        exit 1
    fi

    echo "Copying new cofig file"
    read -p "Database hostname: " dbHost
    read -p "Database name: " dbName
    read -p "Rosetta user password: " dbPassword
    read -p "Database port: " dbPort
    read -p "Rosetta user: " dbUser
    read -p "Rosetta api port: " apiPort
    sed -e 's/${dbHost}/'"${dbHost}"'/g' -e 's/${dbName}/'"${dbName}"'/g' -e 's/${dbPassword}/'"${dbPassword}"'/g' \
        -e 's/${dbPort}/'"${dbPort}"'/g' -e 's/${dbUser}/'"${dbUser}"'/g' -e 's/${apiPort}/'"${apiPort}"'/g' \
        "./${configfile}" >"${configdir}/${configfile}"
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
