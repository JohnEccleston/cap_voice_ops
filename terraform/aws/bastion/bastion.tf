# Keep creds external

provider "aws" {
}

# Network Setup

resource "aws_vpc" "voiceOpsBastionVpc" {
# name                 = "bastionVpc" 
  cidr_block           = "172.30.30.0/24"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags {
    Name              = "Bastion"
  }
}

resource "aws_subnet" "voiceOpsBastionSn1a" {
  vpc_id            = "${aws_vpc.voiceOpsBastionVpc.id}"
  cidr_block        = "172.30.30.0/24"
  availability_zone = "eu-west-1a"

  tags = {
    Name              = "Bastion"
  }
}

resource "aws_internet_gateway" "voiceOpsBastionIgw" {
  vpc_id = "${aws_vpc.voiceOpsBastionVpc.id}"

  tags = {
    Name              = "Bastion"
  }
}

resource "aws_route_table" "voiceOpsBastionRt" {
  vpc_id = "${aws_vpc.voiceOpsBastionVpc.id}"

  tags = {
    Name              = "Bastion"
  }
}

resource "aws_route" "bastionR" {
  route_table_id         = "${aws_route_table.voiceOpsBastionRt.id}"
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = "${aws_internet_gateway.voiceOpsBastionIgw.id}"
}

resource "aws_route_table_association" "voiceOpsBastionRta1a" {
  subnet_id      = "${aws_subnet.voiceOpsBastionSn1a.id}"
  route_table_id = "${aws_route_table.voiceOpsBastionRt.id}"
}

# Security Group Setup

resource "aws_security_group" "voiceOpsBastionSg" {
  name        = "bastion.voiceops.capademy.com"
  vpc_id      = "${aws_vpc.voiceOpsBastionVpc.id}"
  description = "Security group for nodes"

  tags = {
    Name              = "bastion.voiceops.capademy.com"
  }
}

resource "aws_security_group_rule" "SshExternalToBastion" {
  type                     = "ingress"
  security_group_id        = "${aws_security_group.voiceOpsBastionSg.id}"
  from_port                = 22
  to_port                  = 22
  protocol                 = "-1"
  cidr_blocks              = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "bastion-egress" {
  type              = "egress"
  security_group_id = "${aws_security_group.voiceOpsBastionSg.id}"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
}

# IAM Role/Policy Setup

resource "aws_iam_role" "voiceOpsBastionIamRole" {
  name               = "voiceOpsBastion"
  assume_role_policy = "${file("iams/aws_iam_role_voiceOpsBastion_policy.json")}"
}

resource "aws_iam_role_policy" "voiceOpsBastionIamRolePolicy" {
  name   = "voiceOpsBastion"
  role   = "${aws_iam_role.voiceOpsBastionIamRole.name}"
  policy = "${file("iams/aws_iam_role_policy_voiceOpsBastion_policy.json")}"
}

resource "aws_iam_instance_profile" "voiceOpsBastionInstanceProfile" {
  name  = "voiceOpsBastion"
  role  = "${aws_iam_role.voiceOpsBastionIamRole.name}"
}

# Key config

## TBD - need to work out if we want to do this?

# Start of instance creation
# Create instances by setting LCs for instance type
# Then ASGs with sizes

# AWS Launch Configuration Setup

resource "aws_instance" "voiceOpsBastion" {
    ami                         = "ami-7d50491b"
    instance_type               = "t2.micro"
    key_name                    = "voiceOpsBastion"
    security_groups             = ["${aws_security_group.voiceOpsBastionSg.id}"]
    associate_public_ip_address = true
    subnet_id                   = "${aws_subnet.voiceOpsBastionSn1a.id}"

    root_block_device = {
      volume_type           = "gp2"
      volume_size           = 20
      delete_on_termination = true
    }

    provisioner "file" {
      source      = "files/bootstrap.sh"
      destination = "/tmp/bootstrap.sh"
      connection {
        type                  = "ssh"
        user                  = "ec2-user"
        private_key           = "${file("files/voiceOpsBastion.pem")}"
      }
    }
    
    provisioner "remote-exec" {
      inline = [
        "chmod +x /tmp/bootstrap.sh",
        "sudo /tmp/bootstrap.sh",
      ]
      connection {
        type                  = "ssh"
        user                  = "ec2-user"
        private_key           = "${file("files/voiceOpsBastion.pem")}"
      }
    }

    tags = {
      Name              = "Bastion"
      KeepRunning       = "true"
  }
}
