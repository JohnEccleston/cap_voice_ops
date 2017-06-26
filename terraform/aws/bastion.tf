# Variables

# Keep creds external

provider "aws" {
}

# Network Setup

resource "aws_vpc" "voiceOpsBastion" {
# name                 = "bastionVpc" 
  cidr_block           = "172.30.30.0/24"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags {
    Name              = "Bastion"
  }
}

resource "aws_subnet" "voiceOpsBastionSn" {
  vpc_id            = "${aws_vpc.voiceOpsBastion.id}"
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
  vpc_id = "${aws_vpc.voiceOpsVpc.id}"

  tags = {
    Name              = "Bastion"
  }
}

resource "aws_route" "bastionR" {
  route_table_id         = "${aws_route_table.voiceOpsBastionRt.id}"
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = "${aws_internet_gateway.voiceOpsBastionIgw.id}"
}

resource "aws_route_table_association" "voiceOpsBastionRta" {
  subnet_id      = "${aws_subnet.voiceOpsBastionSn.id}"
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

resource "aws_security_group" "voiceOpsBastionSg" {
  name        = "bastion.voiceops.capademy.com"
  vpc_id      = "${aws_vpc.voiceOpsVpc.id}"
  description = "Security group for masters"

  tags = {
    Name              = "bastion.voiceops.capademy.com"
  }
}

resource "aws_security_group_rule" "SshExternalToBastion" {
  type                     = "ingress"
  security_group_id        = "${aws_security_group.voiceOpsBastionSg.id}"
  source_security_group_id = "${aws_security_group.voiceOpsBastionSg.id}"
  from_port                = 22
  to_port                  = 22
  protocol                 = "-1"
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

# K8 Master launch config
resource "aws_launch_configuration" "voiceOpsMaster" {
    name                        = "voiceOpsMaster"
    image_id                    = "ami-7d50491b"
    instance_type               = "t2.medium"
    key_name                    = "demo"
    iam_instance_profile        = "${aws_iam_instance_profile.voiceOpsMastersInstanceProfile.id}"
    security_groups             = ["${aws_security_group.voiceOpsMastersSg.id}"]
    associate_public_ip_address = true
    root_block_device = {
      volume_type           = "gp2"
      volume_size           = 20
      delete_on_termination = true
    }

    lifecycle = {
      create_before_destroy = true
    }
}

# K8 Node launch config

resource "aws_launch_configuration" "voiceOpsNode" {
    name                        = "voiceOpsNode"
    image_id                    = "ami-7d50491b"
    instance_type               = "t2.large"
    key_name                    = "demo"
    iam_instance_profile        = "${aws_iam_instance_profile.voiceOpsNodesInstanceProfile.id}"
    security_groups             = ["${aws_security_group.voiceOpsNodesSg.id}"]
    associate_public_ip_address = true

    root_block_device = {
      volume_type           = "gp2"
      volume_size           = 20
      delete_on_termination = true
    }

    lifecycle = {
      create_before_destroy = true
    }
}

# Bastion host launch config
resource "aws_launch_configuration" "voiceOpsBastion" {
    name                        = "voiceOpsBastion"
    image_id                    = "ami-7d50491b"
    instance_type               = "t2.micro"
    key_name                    = "demo"
    security_groups             = ["${aws_security_group.voiceOpsMastersSg.id}"]
    associate_public_ip_address = true

    root_block_device = {
      volume_type           = "gp2"
      volume_size           = 20
      delete_on_termination = true
    }

    lifecycle = {
      create_before_destroy = true
    }
}

# AWS Auto Scaling Group Setup

# K8 Node ASG
resource "aws_autoscaling_group" "voiceOpsNodeASG" {
  name                 = "voiceOpsNodeASG"
  max_size             = 3
  min_size             = 3
  desired_capacity     = 3
  health_check_type    = "EC2"
  force_delete         = true
  launch_configuration = "${aws_launch_configuration.voiceOpsNode.name}"
  vpc_zone_identifier  = ["${aws_subnet.voiceOpsSn1c.id}","${aws_subnet.voiceOpsSn1b.id}","${aws_subnet.voiceOpsSn1a.id}"]

}

# K8 Master ASG
resource "aws_autoscaling_group" "voiceOpsMasterASG1a" {
  name                 = "voiceOpsMasterASG1a"
  max_size             = 1
  min_size             = 1
  desired_capacity     = 1
  health_check_type    = "EC2"
  force_delete         = true
  launch_configuration = "${aws_launch_configuration.voiceOpsMaster.name}"
  vpc_zone_identifier  = ["${aws_subnet.voiceOpsSn1a.id}"]

}

resource "aws_autoscaling_group" "voiceOpsMasterASG1b" {
  name                 = "voiceOpsMasterASG1b"
  max_size             = 1
  min_size             = 1
  desired_capacity     = 1
  health_check_type    = "EC2"
  force_delete         = true
  launch_configuration = "${aws_launch_configuration.voiceOpsMaster.name}"
  vpc_zone_identifier  = ["${aws_subnet.voiceOpsSn1b.id}"]

}

resource "aws_autoscaling_group" "voiceOpsMasterASG1c" {
  name = "voiceOpsMasterASG1c"
  max_size             = 1
  min_size             = 1
  desired_capacity     = 1
  health_check_type    = "EC2"
  force_delete         = true
  launch_configuration = "${aws_launch_configuration.voiceOpsMaster.name}"
  vpc_zone_identifier  = ["${aws_subnet.voiceOpsSn1c.id}"]

}


# K8 Bastion ASG
resource "aws_autoscaling_group" "voiceOpsBastionASG" {
  name                 = "voiceOpsBastionASG"
  max_size             = 1
  min_size             = 1
  desired_capacity     = 1
  health_check_type    = "EC2"
  force_delete         = true
  launch_configuration = "${aws_launch_configuration.voiceOpsBastion.name}"
  vpc_zone_identifier  = ["${aws_subnet.voiceOpsSn1c.id}","${aws_subnet.voiceOpsSn1b.id}","${aws_subnet.voiceOpsSn1a.id}"]

}


# Maybe create some volumes for etcd?  Feels like this
# may be better created in ansible during the k8s spin.
