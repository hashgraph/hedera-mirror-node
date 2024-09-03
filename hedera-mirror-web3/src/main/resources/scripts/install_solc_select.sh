#!/bin/bash
set -euo pipefail

# Export the PIP3 bin path
export PATH="${HOME}/.local/bin:${PATH}"

# Function to install pip3 and solc-select on Ubuntu/Debian
install_on_debian() {
    sudo apt update
    sudo apt install -y python3 python3-pip
    pip3 install solc-select
}

# Function to install pip3 and solc-select on Fedora
install_on_fedora() {
    sudo dnf install -y python3 python3-pip
    pip3 install solc-select
}

# Function to install pip3 and solc-select on macOS
install_on_macos() {
    if (! brew &> /dev/null); then
        echo "Homebrew not found, installing Homebrew..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    fi
    brew install solc-select
}

# Function to check if solc-select is installed
check_solc_select_installed() {
    if (solc-select >/dev/null 2>&1); then
        echo "solc-select is already installed."
        return 0
    else
        echo "solc-select is not installed."
        return 1
    fi
}

# Function to install solc-select
install_solc_select() {
   case $OS in
       "Linux")
           # Detect the Linux distribution
           if [[ -f /etc/debian_version ]]; then
               echo "Detected Debian-based Linux distribution."
               install_on_debian
           elif [[ -f /etc/fedora-release ]]; then
               echo "Detected Fedora-based Linux distribution."
               install_on_fedora
           else
               echo "Unsupported Linux distribution."
           fi
           ;;
       "Darwin")
           echo "Detected macOS."
           install_on_macos
           ;;
       *)
           echo "Unsupported OS: $OS"
           exit 1
           ;;
   esac
}

# Detect the operating system
OS=$(uname)

if (! check_solc_select_installed); then
    install_solc_select
fi
