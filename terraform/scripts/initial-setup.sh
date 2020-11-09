#!/usr/bin/env bash
set -eux

# Install prereqs
sudo apt-get update
sudo apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg-agent \
    software-properties-common

# Install docker
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo apt-key fingerprint 0EBFCD88
sudo add-apt-repository \
   "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
   $(lsb_release -cs) \
   stable"
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io
sudo usermod -aG docker ubuntu
sudo systemctl start docker

# Install docker-compose
sudo wget -O  /usr/local/bin/docker-compose https://github.com/docker/compose/releases/download/1.27.4/docker-compose-Linux-x86_64
sudo chmod +x /usr/local/bin/docker-compose

# Install java8
sudo apt-get install -y openjdk-8-jdk maven

# Install kafka
sudo wget https://downloads.apache.org/kafka/2.5.1/kafka_2.12-2.5.1.tgz
sudo mkdir -p /opt/kafka
sudo tar -C /opt/kafka -xzf kafka_2.12-2.5.1.tgz --strip-components=1
for file in $(ls -1 /opt/kafka/bin/*.sh); do sudo cp "$file" "${file%.sh}"; done
sudo sed -e 's|PATH="\(.*\)"|PATH="/opt/kafka/bin:\1"|g' -i /etc/environment

sudo touch /home/ubuntu/.finished
