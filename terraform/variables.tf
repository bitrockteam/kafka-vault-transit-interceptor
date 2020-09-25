variable "kafka_instance_type" {
  default = "r5.xlarge"
}
variable "kafka_root_volume_size" {
  default = "30"
}
variable "vault_instance_type" {
  default = "r5.xlarge"
}
variable "vault_root_volume_size" {
  default = "30"
}
variable "test_runner_instance_type" {
  default = "r5.xlarge"
}
variable "test_runner_root_volume_size" {
  default = "30"
}
variable "region" {
  default = "us-east-1"
}
variable "base_folder_dir" {
  default = "/Users/sripamonti/Projects/Bitrock/kafka-vault-transit-interceptor"
}