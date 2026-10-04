#!/usr/bin/env bash
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "bootstrap: run as root, for example with sudo" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git iptables-persistent unattended-upgrades

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
# shellcheck source=/dev/null
. /etc/os-release
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Oracle Cloud's Ubuntu image rejects these ports in iptables; harmless on EC2.
for port in 80 443; do
  rule=(-p tcp -m state --state NEW -m tcp --dport "$port" -j ACCEPT)
  iptables -C INPUT "${rule[@]}" 2>/dev/null || iptables -I INPUT "${rule[@]}"
  # Edited in place: netfilter-persistent save would also persist Docker's own chains.
  grep -qe "--dport $port -j ACCEPT" /etc/iptables/rules.v4 ||
    sed -i "0,/^-A INPUT -j REJECT/s//-A INPUT ${rule[*]}\n&/" /etc/iptables/rules.v4
done

if [[ ! -f /swapfile ]]; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
fi
swapon --show=NAME --noheadings | grep -qx /swapfile || swapon /swapfile
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab

printf 'APT::Periodic::Update-Package-Lists "1";\nAPT::Periodic::Unattended-Upgrade "1";\n' \
  > /etc/apt/apt.conf.d/20auto-upgrades

id deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash deploy
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
touch /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

if [[ ! -d /opt/au-van/.git ]]; then
  git clone --single-branch --branch main https://github.com/NoelPOS/au-van-platform.git /opt/au-van
fi
chown -R deploy:deploy /opt/au-van

echo '0 3 * * * deploy /opt/au-van/deploy/backup.sh 2>&1 | logger -t au-van-backup' > /etc/cron.d/au-van-backup
