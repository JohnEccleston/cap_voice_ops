variable "masters_number" {
  type    = "string"
  default = "1"
}

variable "nodes_number" {
  type    = "string"
  default = "3"
}

provider "aws" {
  region = "eu-west-1"
}

resource "aws_vpc" "voiceOpsVpc" {
  name                 = "voiceOpsVpc" 
  cidr_block           = "172.20.0.0/20"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags {
    KubernetesCluster = "voiceOps"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "voiceOpsSn1a" {
  vpc_id            = "${aws_vpc.voiceOpsVpc"
  cidr_block        = "172.20.0.0/24"
  availability_zone = "eu-west-1a"

  tags = {
    KubernetesCluster = "voiceOps"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "voiceOpsSn1b" {
  vpc_id            = "${aws_vpc.voiceOpsVpc"
  cidr_block        = "172.20.1.0/24"
  availability_zone = "eu-west-1b"

  tags = {
    KubernetesCluster = "voiceOps"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "voiceOpsSn1c" {
  vpc_id            = "${aws_vpc.voiceOpsVpc"
  cidr_block        = "172.20.2.0/24"
  availability_zone = "eu-west-1c"

  tags = {
    KubernetesCluster = "voiceOps"
    Name              = "voiceOps"
  }
}
