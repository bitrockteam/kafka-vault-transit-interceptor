provider "aws" {
  region = var.region
}

provider "null" {

}

data "aws_ami" "ubuntu" {
  most_recent = true
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-focal-20.04-amd64-server-*"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
  owners = ["099720109477"] # Canonical
}

data "template_file" "perf_test_sh" {
  template = file("${path.root}/scripts/perf-test.sh.tmpl")
  vars = {
    vault_address = aws_instance.vault.private_ip,
    kafka_address = aws_instance.kafka.private_ip
  }
}

resource "tls_private_key" "this" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "aws_key_pair" "this" {
  key_name_prefix = "kafka-vault"
  public_key = tls_private_key.this.public_key_openssh
}

resource "aws_vpc" "this" {
  cidr_block = "172.16.0.0/16"
  tags       = local.terratag_added_main
}

resource "aws_subnet" "this" {
  cidr_block        = "172.16.0.0/24"
  vpc_id            = aws_vpc.this.id
  availability_zone = "us-east-1a"
  tags              = local.terratag_added_main
}

resource "aws_security_group" "kafka" {
  vpc_id = aws_vpc.this.id
  ingress {
    from_port = 9092
    to_port = 9092
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "vault" {
  vpc_id = aws_vpc.this.id
  ingress {
    from_port = 8200
    to_port = 8200
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "shared" {
  vpc_id = aws_vpc.this.id
  ingress {
    from_port = 22
    to_port = 22
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port = 0
    protocol = "-1"
    to_port = 0
    cidr_blocks = ["0.0.0.0/0"]
  }
}



resource "aws_instance" "kafka" {
  ami           = data.aws_ami.ubuntu.id
  instance_type = var.kafka_instance_type
  root_block_device {
    volume_size = var.kafka_root_volume_size
  }
  subnet_id = aws_subnet.this.id
  tags      = local.terratag_added_main

  user_data = file("${path.root}/scripts/initial-setup.sh")
  key_name = aws_key_pair.this.id
  vpc_security_group_ids = [aws_security_group.kafka.id, aws_security_group.shared.id]
  associate_public_ip_address = true

  connection {
    type = "ssh"
    user = "ubuntu"
    private_key = tls_private_key.this.private_key_pem
    host = self.public_ip
  }

  provisioner "remote-exec" {
    inline = [
      "echo 'ready'"
    ]
  }

  provisioner "local-exec" {
    command = "rsync -avzO -e 'ssh -i ${path.root}/key.pem -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' --filter='dir-merge,- ../.gitignore' --progress ${pathexpand(var.base_folder_dir)} ubuntu@${aws_instance.kafka.public_ip}:/home/ubuntu"
  }

  provisioner "remote-exec" {
    inline = [
      "while [ ! -f /home/ubuntu/.finished ] ; do sleep 2 ; done",
      "while sudo -i -u ubuntu docker ps ; ret=$? ; [ $ret -ne 0 ] ; do sleep 2 ; done",
      "cd /home/ubuntu/kafka-vault-transit-interceptor/ && mvn package",
      "cd /home/ubuntu/kafka-vault-transit-interceptor && sudo -u ubuntu docker-compose -f docker/docker-compose.yaml up -d zookeeper kafka",
      "sudo -i -u ubuntu docker ps"
    ]
  }
}

resource "aws_instance" "vault" {
  ami           = data.aws_ami.ubuntu.id
  instance_type = var.vault_instance_type
  root_block_device {
    volume_size = var.vault_root_volume_size
  }
  subnet_id = aws_subnet.this.id
  tags      = local.terratag_added_main

  user_data = file("${path.root}/scripts/initial-setup.sh")
  key_name = aws_key_pair.this.id
  vpc_security_group_ids = [aws_security_group.vault.id, aws_security_group.shared.id]
  associate_public_ip_address = true

  connection {
    type = "ssh"
    user = "ubuntu"
    private_key = tls_private_key.this.private_key_pem
    host = self.public_ip
  }

  provisioner "remote-exec" {
    inline = [
      "echo 'ready'"
    ]
  }

  provisioner "local-exec" {
    command = "rsync -avzO -e 'ssh -i ${path.root}/key.pem -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' --filter='dir-merge,- ../.gitignore' --progress ${pathexpand(var.base_folder_dir)} ubuntu@${aws_instance.vault.public_ip}:/home/ubuntu"
  }

  provisioner "remote-exec" {
    inline = [
      "while [ ! -f /home/ubuntu/.finished ] ; do sleep 2 ; done",
      "while sudo -i -u ubuntu docker ps ; ret=$? ; [ $ret -ne 0 ] ; do sleep 2 ; done",
      "chmod +x /home/ubuntu/kafka-vault-transit-interceptor/docker/vault/docker-entrypoint.sh",
      "cd /home/ubuntu/kafka-vault-transit-interceptor && sudo -u ubuntu docker-compose -f docker/docker-compose.yaml up -d vault",
      "sudo -i -u ubuntu docker ps"
    ]
  }
}

resource "aws_instance" "test_runner" {
  ami           = data.aws_ami.ubuntu.id
  instance_type = var.test_runner_instance_type
  root_block_device {
    volume_size = var.test_runner_root_volume_size
  }
  subnet_id = aws_subnet.this.id
  tags      = local.terratag_added_main

  user_data = file("${path.root}/scripts/initial-setup.sh")
  key_name = aws_key_pair.this.id
  vpc_security_group_ids = [aws_security_group.shared.id]
  associate_public_ip_address = true

  connection {
    type = "ssh"
    user = "ubuntu"
    private_key = tls_private_key.this.private_key_pem
    host = self.public_ip
  }

  provisioner "remote-exec" {
    inline = [
      "echo 'ready'"
    ]
  }

  provisioner "local-exec" {
    command = "rsync -avzO -e 'ssh -i ${path.root}/key.pem -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' --filter='dir-merge,- ../.gitignore' --progress ${pathexpand(var.base_folder_dir)} ubuntu@${aws_instance.test_runner.public_ip}:/home/ubuntu"
  }

  provisioner "file" {
    content = data.template_file.perf_test_sh.rendered
    destination = "/home/ubuntu/kafka-vault-transit-interceptor/perf-test-rendered.sh"
  }
}

locals {
  terratag_added_main = {"scope"="kafka-vault-transit-interceptor"}
}

resource "local_file" "private_key" {
  filename = "${path.root}/key.pem"
  content = tls_private_key.this.private_key_pem
}

resource "null_resource" "key_chown" {
  provisioner "local-exec" {
    command = "chmod 400 ${path.root}/key.pem"
  }

  depends_on = [local_file.private_key]
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
}
resource "aws_route" "internet" {
  route_table_id = aws_vpc.this.main_route_table_id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id = aws_internet_gateway.this.id
}
