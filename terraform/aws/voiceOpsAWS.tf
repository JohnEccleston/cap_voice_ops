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

resource "aws_vpc" "voiceOpsVPC" {
  name                 = "voiceOpsVPC" 
  cidr_block           = "172.20.0.0/22"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags {
    KubernetesCluster = "voiceOps"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "eu-west-1c-k8sdemo-capademy-com" {
  vpc_id            = "${aws_vpc.k8sdemo-capademy-com.id}"
  cidr_block        = "172.20.0.0/24"
  availability_zone = "eu-west-1c"

  tags = {
    KubernetesCluster = "voiceOps"
    Name              = "voiceOps"
  }
}
