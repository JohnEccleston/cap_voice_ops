# Variables

variable "masters_number" {
  type    = "string"
  default = "1"
}

variable "nodes_number" {
  type    = "string"
  default = "3"
}

# Keep creds external

provider "aws" {
  region = "eu-west-1"
}

# Network Setup

resource "aws_vpc" "voiceOpsVpc" {
  name                 = "voiceOpsVpc" 
  cidr_block           = "172.20.0.0/20"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "voiceOpsSn1a" {
  vpc_id            = "${aws_vpc.voiceOpsVpc"
  cidr_block        = "172.20.0.0/24"
  availability_zone = "eu-west-1a"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "voiceOpsSn1b" {
  vpc_id            = "${aws_vpc.voiceOpsVpc"
  cidr_block        = "172.20.1.0/24"
  availability_zone = "eu-west-1b"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "voiceOpsSn1c" {
  vpc_id            = "${aws_vpc.voiceOpsVpc"
  cidr_block        = "172.20.2.0/24"
  availability_zone = "eu-west-1c"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_internet_gateway" "voiceOpsIgw" {
  vpc_id = "${aws_vpc.k8sdemo-capademy-com.id}"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_route_table" "voiceOpsRt" {
  vpc_id = "${aws_vpc.voiceOpsVpc.id}"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_route" "0-0-0-0--0" {
  route_table_id         = "${aws_route_table.voiceOpsRt.id}"
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = "${aws_internet_gateway.voiceOpsIgw.id}"
}

resource "aws_route_table_association" "voiceOpsRta1a" {
  subnet_id      = "${aws_subnet.voiceOpsSn1a.id}"
  route_table_id = "${aws_route_table.voiceOpsRt.id}"
}

resource "aws_route_table_association" "voiceOpsRta1b" {
  subnet_id      = "${aws_subnet.voiceOpsSn1b.id}"
  route_table_id = "${aws_route_table.voiceOpsRt.id}"
}

resource "aws_route_table_association" "voiceOpsRta1c" {
  subnet_id      = "${aws_subnet.voiceOpsSn1c.id}"
  route_table_id = "${aws_route_table.voiceOpsRt.id}"
}

# Security Group Setup

## TBD

# IAM Role/Policy Setup

## TBD

# Start of instance creation
# Create instances by setting LCs for instance type
# Then ASGs with sizes

# AWS Launch Configuration Setup

## TBD

# AWS Auto Scaling Group Setup

## TBD

# Maybe create some volumes for etcd?  Feels like this
# may be better created in ansible during the k8s spin.
