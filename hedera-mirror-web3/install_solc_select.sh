#!/bin/bash

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
    if ! command -v brew &> /dev/null; then
        echo "Homebrew not found, installing Homebrew..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    fi
    brew install python3
    pip3 install solc-select
}

# Detect the operating system
OS=$(uname)

case $OS in
    "Linux")
        # Detect the Linux distribution
        if [ -f /etc/debian_version ]; then
            echo "Detected Debian-based Linux distribution."
            install_on_debian
        elif [ -f /etc/fedora-release ]; then
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
        ;;
esac
