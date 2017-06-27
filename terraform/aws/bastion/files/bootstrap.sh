#!/bin/bash

yum -y update
yum -y install git
yum -y install epel-release
yum -y --enablerepo=epel install ansible

# Install Terraform
cd /tmp
wget https://releases.hashicorp.com/terraform/0.9.9/terraform_0.9.9_linux_amd64.zip
unzip terraform_0.9.9_linux_amd64.zip
mv terraform /usr/local/bin/

su ec2-user <<'EOF'
  cd ~
  git clone https://github.com/JohnEccleston/cap_voice_ops.git
EOF
