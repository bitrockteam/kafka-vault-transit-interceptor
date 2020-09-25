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

# Install docker-compose
sudo wget -O  /usr/local/bin/docker-compose https://github.com/docker/compose/releases/download/1.25.0/docker-compose-Linux-x86_64
sudo chmod +x /usr/local/bin/docker-compose

# Install java8
sudo apt-get install -y openjdk-8-jdk maven

# Install kafka
sudo wget https://downloads.apache.org/kafka/2.5.1/kafka_2.12-2.5.1.tgz
sudo tar xzf kafka_2.12-2.5.1.tgz
sudo mv kafka_2.12-2.5.1.tgz /usr/local/kafka

sudo usermod -aG docker ubuntu

sudo touch /home/ubuntu/.finished
