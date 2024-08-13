#!/bin/bash
set -euo pipefail

tag_name=$(curl --silent "https://api.github.com/repos/bilyana-gospodinova/web3j-cli/releases/latest" | jq -r .tag_name)
web3j_version=$(echo $tag_name | sed 's/v//')
installed_flag=0
installed_version=""

check_if_installed() {
  if command -v web3j >/dev/null 2>&1; then
    printf 'A Web3j installation exists on your system.\n'
    installed_flag=1
  fi
}

setup_color() {
  # Only use colors if connected to a terminal
  if [ -t 1 ]; then
    RED=$(printf '\033[31m')
    GREEN=$(printf '\033[32m')
    YELLOW=$(printf '\033[33m')
    BLUE=$(printf '\033[34m')
    BOLD=$(printf '\033[1m')
    RESET=$(printf '\033[m')
  else
    RED=""
    GREEN=""
    YELLOW=""
    BLUE=""
    BOLD=""
    RESET=""
  fi
}

install_web3j() {
  echo "Downloading Web3j ..."
  mkdir -p "$HOME/.web3j"
  if [ "$(curl --write-out "%{http_code}" --silent --output /dev/null "https://github.com/bilyana-gospodinova/web3j-cli/releases/download/${web3j_version}/web3j-cli-shadow-${web3j_version}.tar")" -eq 302 ]; then
    curl -# -L -o "$HOME/.web3j/web3j-cli-shadow-${web3j_version}.tar" "https://github.com/bilyana-gospodinova/web3j-cli/releases/download/${web3j_version}/web3j-cli-shadow-${web3j_version}.tar"
    echo "Installing Web3j..."
    tar -xf "$HOME/.web3j/web3j-cli-shadow-${web3j_version}.tar" -C "$HOME/.web3j"
    echo "export PATH=\$PATH:$HOME/.web3j" >"$HOME/.web3j/source.sh"
    chmod +x "$HOME/.web3j/source.sh"
    echo "Removing downloaded archive..."
    rm "$HOME/.web3j/web3j-cli-shadow-${web3j_version}.tar"
  else
    echo "Looks like there was an error while trying to download Web3j"
    exit 0
  fi
}

get_user_input() {
  while echo "Would you like to update Web3j [Y/n]" && read -r user_input </dev/tty ; do
    case $user_input in
    n)
      echo "Aborting installation ..."
      exit 0
      ;;
    *)
      echo "Updating Web3j ..."
      break
      ;;
    esac
  done
}

check_version() {
  installed_version=$(web3j --version | grep Version | awk -F" " '{print $NF}')
  if [ "$installed_version" = "$web3j_version" ]; then
    echo "You have the latest version of Web3j (${installed_version}). Exiting."
    exit 0
  else
    echo "Your Web3j version is not up to date."
    get_user_input
  fi
}

source_web3j() {
  SOURCE_Web3j="\n[ -s \"$HOME/.web3j/source.sh\" ] && source \"$HOME/.web3j/source.sh\""

  # Handle different shell profiles
  for file in "$HOME/.bashrc" "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile" "$HOME/.zshrc"; do
    if [ -f "$file" ]; then
      touch "${file}"
      if ! grep -qc '.web3j/source.sh' "${file}"; then
        echo "Adding source string to ${file}"
        printf "$SOURCE_Web3j\n" >>"${file}"
      else
        echo "Skipped update of ${file} (source string already present)"
      fi
    fi
  done
}

clean_up() {
  if [ -d "$HOME/.web3j" ]; then
    rm -f "$HOME/.web3j/source.sh"
    rm -rf "$HOME/.web3j/web3j-cli-shadow-$installed_version" >/dev/null 2>&1
    echo "Deleting older installation ..."
  fi
}

completed() {
  cd "$HOME/.web3j"
  ln -sf "web3j-cli-shadow-$web3j_version/bin/web3j" web3j
  ls -l "$HOME/.web3j"
  printf '\n'
  printf "$GREEN"
  echo "Web3j was successfully installed."
  echo "To use Web3j in your current shell, run:"
  echo "source \$HOME/.web3j/source.sh"
  echo "When you open a new shell, this will be performed automatically."
  echo "To see what Web3j's CLI can do, you can check the documentation below."
  echo "https://docs.web3j.io/latest/command_line_tools/"
  printf "$RESET"
  exit 0
}

check_java_version() {
  java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
  echo "Your current Java version is ${java_version}"
  major_version=$(echo "$java_version" | cut -d'.' -f1)
  if [ "$major_version" -ge 17 ]; then
    echo "Your Java version is compatible with Web3j CLI."
  else
    echo "The Web3j CLI requires a Java version of 17 or higher. Please ensure you have a compatible Java version before installing Web3j for full functionality."
    read -r -s -n 1 -p "Press any key to continue, or press Ctrl+C to cancel the installation." </dev/tty
  fi
}

main() {
  setup_color
  check_java_version
  check_if_installed
  if [ $installed_flag -eq 1 ]; then
    check_version
    clean_up
    install_web3j
    source_web3j
    completed
  else
    install_web3j
    source_web3j
    completed
  fi
}

main
