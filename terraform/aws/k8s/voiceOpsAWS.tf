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
#    access_key = "${var.aws_access_key}"
#    secret_key = "${var.aws_secret_key}"
#    region = "eu-west-1"
}

# Network Setup

resource "aws_vpc" "voiceOpsVpc" {
# name                 = "voiceOpsVpc" 
  cidr_block           = "172.20.0.0/20"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "voiceOpsSn1a" {
  vpc_id            = "${aws_vpc.voiceOpsVpc.id}"
  cidr_block        = "172.20.0.0/24"
  availability_zone = "eu-west-1a"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "voiceOpsSn1b" {
  vpc_id            = "${aws_vpc.voiceOpsVpc.id}"
  cidr_block        = "172.20.1.0/24"
  availability_zone = "eu-west-1b"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_subnet" "voiceOpsSn1c" {
  vpc_id            = "${aws_vpc.voiceOpsVpc.id}"
  cidr_block        = "172.20.2.0/24"
  availability_zone = "eu-west-1c"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "voiceOps"
  }
}

resource "aws_internet_gateway" "voiceOpsIgw" {
  vpc_id = "${aws_vpc.voiceOpsVpc.id}"

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

resource "aws_security_group" "voiceOpsNodesSg" {
  name        = "nodes.voiceops.capademy.com"
  vpc_id      = "${aws_vpc.voiceOpsVpc.id}"
  description = "Security group for nodes"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "nodes.voiceops.capademy.com"
  }
}

resource "aws_security_group" "voiceOpsMastersSg" {
  name        = "masters.voiceops.capademy.com"
  vpc_id      = "${aws_vpc.voiceOpsVpc.id}"
  description = "Security group for masters"

  tags = {
    KubernetesCluster = "voiceops.capademy.com"
    Name              = "masters.voiceops.capademy.com"
  }
}

resource "aws_security_group_rule" "all-master-to-master" {
  type                     = "ingress"
  security_group_id        = "${aws_security_group.voiceOpsMastersSg.id}"
  source_security_group_id = "${aws_security_group.voiceOpsMastersSg.id}"
  from_port                = 0
  to_port                  = 0
  protocol                 = "-1"
}

resource "aws_security_group_rule" "all-master-to-node" {
  type                     = "ingress"
  security_group_id        = "${aws_security_group.voiceOpsNodesSg.id}"
  source_security_group_id = "${aws_security_group.voiceOpsMastersSg.id}"
  from_port                = 0
  to_port                  = 0
  protocol                 = "-1"
}

resource "aws_security_group_rule" "all-node-to-node" {
  type                     = "ingress"
  security_group_id        = "${aws_security_group.voiceOpsNodesSg.id}"
  source_security_group_id = "${aws_security_group.voiceOpsNodesSg.id}"
  from_port                = 0
  to_port                  = 0
  protocol                 = "-1"
}

resource "aws_security_group_rule" "https-external-to-master-443" {
  type              = "ingress"
  security_group_id = "${aws_security_group.voiceOpsMastersSg.id}"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "https-external-to-master-22" {
  type              = "ingress"
  security_group_id = "${aws_security_group.voiceOpsMastersSg.id}"
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "https-external-to-nodes-22" {
  type              = "ingress"
  security_group_id = "${aws_security_group.voiceOpsNodesSg.id}"
  from_port         = 22
  to_port           = 22
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "master-egress" {
  type              = "egress"
  security_group_id = "${aws_security_group.voiceOpsMastersSg.id}"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "node-egress" {
  type              = "egress"
  security_group_id = "${aws_security_group.voiceOpsNodesSg.id}"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "node-to-master-tcp-4194" {
  type                     = "ingress"
  security_group_id        = "${aws_security_group.voiceOpsMastersSg.id}"
  source_security_group_id = "${aws_security_group.voiceOpsNodesSg.id}"
  from_port                = 4194
  to_port                  = 4194
  protocol                 = "tcp"
}

resource "aws_security_group_rule" "node-to-master-tcp-443" {
  type                     = "ingress"
  security_group_id        = "${aws_security_group.voiceOpsMastersSg.id}"
  source_security_group_id = "${aws_security_group.voiceOpsNodesSg.id}"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
}

# IAM Role/Policy Setup

resource "aws_iam_role" "voiceOpsMastersIamRole" {
  name               = "voiceOpsMasters"
  assume_role_policy = "${file("iams/aws_iam_role_voiceOpsMasters_policy.json")}"
}

resource "aws_iam_role" "voiceOpsNodesIamRole" {
  name               = "voiceOpsNodes"
  assume_role_policy = "${file("iams/aws_iam_role_voiceOpsNodes_policy.json")}"
}

resource "aws_iam_role_policy" "voiceOpsMastersIamRolePolicy" {
  name   = "voiceOpsMasters"
  role   = "${aws_iam_role.voiceOpsMastersIamRole.name}"
  policy = "${file("iams/aws_iam_role_policy_voiceOpsMasters_policy.json")}"
}

resource "aws_iam_role_policy" "voiceOpsNodesIamRolePolicy" {
  name   = "voiceOpsNodes"
  role   = "${aws_iam_role.voiceOpsNodesIamRole.name}"
  policy = "${file("iams/aws_iam_role_policy_voiceOpsNodes_policy.json")}"
}

resource "aws_iam_instance_profile" "voiceOpsMastersInstanceProfile" {
  name  = "voiceOpsMasters"
  role  = "${aws_iam_role.voiceOpsMastersIamRole.name}"
}

resource "aws_iam_instance_profile" "voiceOpsNodesInstanceProfile" {
  name  = "voiceOpsNodes"
  role  = "${aws_iam_role.voiceOpsNodesIamRole.name}"
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
    image_id                    = "ami-061b1560"
    instance_type               = "t2.medium"
    key_name                    = "voiceOpsk8s"
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
    image_id                    = "ami-061b1560"
    instance_type               = "t2.large"
    key_name                    = "voiceOpsk8s"
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


# Maybe create some volumes for etcd?  Feels like this
# may be better created in ansible during the k8s spin.
