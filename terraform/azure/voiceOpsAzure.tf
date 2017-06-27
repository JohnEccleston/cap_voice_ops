variable "masters_number" {
  type    = "string"
  default = "1"
}

variable "nodes_number" {
  type    = "string"
  default = "3"
}

#variable "subscription_id" {}
#variable "client_id" {}
#variable "client_secret" {}
#variable "tenant_id" {}


provider "azurerm" {
# Creds provided by environment variables
#  subscription_id = "${var.subscription_id}"
#  client_id       = "${var.client_id}"
#  client_secret   = "${var.client_secret}"
#  tenant_id       = "${var.tenant_id}"
}

# Create resource group

resource "azurerm_resource_group" "voiceOpsRm" {
  name     = "voiceOpsResourceGroup"
  location = "UK West"

  tags {
    environment = "Production"
  }
}

# Network Setup

resource "azurerm_virtual_network" "voiceOpsVn" {
  name          = "voiceOpsNetwork"
  address_space = ["172.21.0.0/22"]
  location      = "UK West"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  tags {
    environment = "Production"
  }
}

resource "azurerm_subnet" voiceOpsSnMasters {
    name           = "masters"
    address_prefix = "172.21.0.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    virtual_network_name = "${azurerm_virtual_network.voiceOpsVn.name}"
}

resource "azurerm_subnet" voiceOpsSnNodes {
    name          = "nodes"
    address_prefix = "172.21.1.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    virtual_network_name = "${azurerm_virtual_network.voiceOpsVn.name}"
}
  
# Create Availability Set for Masters

resource "azurerm_availability_set" "voiceOpsAsMasters" {
  name                = "voiceOpsMastersAs"
  location            = "UK West"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  tags {
    environment = "Production"
  }
}

# Create Availability Set for Nodes

resource "azurerm_availability_set" "voiceOpsAsNodes" {
  name                = "voiceOpsNodesAs"
  location            = "UK West"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  tags {
    environment = "Production"
  }
}

resource "azurerm_network_interface" "voiceOpsNiMasters" {
  count               = "${var.masters_number}"
  name                = "vonim${count.index}"
  location            = "UK West"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
  network_security_group_id = "${azurerm_network_security_group.voiceopsmaster_security_group.id}"

  ip_configuration {
    name                          = "voiceOpsIpConfigMasters"
    subnet_id                     = "${azurerm_subnet.voiceOpsSnMasters.id}"
    private_ip_address_allocation = "dynamic"
  }
}

resource "azurerm_network_interface" "voiceOpsNiNodes" {
  count               = "${var.nodes_number}"
  name                = "vonin${count.index}"
  location            = "UK West"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
  network_security_group_id = "${azurerm_network_security_group.voiceopsnode_security_group.id}"

  ip_configuration {
    name                          = "voiceOpsIpConfigNodes"
    subnet_id                     = "${azurerm_subnet.voiceOpsSnNodes.id}"
    private_ip_address_allocation = "dynamic"
  }
}

resource "azurerm_virtual_machine" "voiceOpsMasters" {
  count                 = "${var.masters_number}"
  name                  = "vomvm${count.index}"
  location              = "UK West"
  resource_group_name   = "${azurerm_resource_group.voiceOpsRm.name}"
  network_interface_ids = ["${element(azurerm_network_interface.voiceOpsNiMasters.*.id, count.index)}"]
  vm_size               = "Standard_A1_v2"

  storage_image_reference {
    publisher = "OpenLogic"
    offer     = "CentOS"
    sku       = "7.2"
    version   = "latest"
  }

  storage_os_disk {
    name              = "osdiskm${count.index}"
    caching           = "ReadWrite"
    create_option     = "FromImage"
    managed_disk_type = "Standard_LRS"
    disk_size_gb      = "80"
  }

  os_profile {
    computer_name  = "voiceops-master${count.index}"
    admin_username = "voiceops"
    admin_password = "voiceOpsbatterystaple1986"
  }

  os_profile_linux_config {
    disable_password_authentication = false
  }

  tags {
    environment = "production"
  }
}

resource "azurerm_virtual_machine" "voiceOpsNodes" {
  count                 = "${var.nodes_number}"
  name                  = "vonvm${count.index}"
  location              = "UK West"
  resource_group_name   = "${azurerm_resource_group.voiceOpsRm.name}"
  network_interface_ids = ["${element(azurerm_network_interface.voiceOpsNiNodes.*.id, count.index)}"]
  vm_size               = "Standard_A1_v2"

  storage_image_reference {
    publisher = "OpenLogic"
    offer     = "CentOS"
    sku       = "7.2"
    version   = "latest"
  }

  storage_os_disk {
    name              = "osdiskn${count.index}"
    caching           = "ReadWrite"
    create_option     = "FromImage"
    managed_disk_type = "Standard_LRS"
    disk_size_gb      = "80"
  }

  os_profile {
    computer_name  = "voiceops-node${count.index}"
    admin_username = "voiceops"
    admin_password = "voiceOpsbatterystaple1986"
  }

  os_profile_linux_config {
    disable_password_authentication = false
  }

  tags {
    environment = "production"
  }
}



#Security Groups for the Masters
resource "azurerm_network_security_group" "voiceopsmaster_security_group" {
    name = "voiceopsmaster_security_group"
    location = "UK West"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

    tags {
        environment = "production"
    }
}

resource "azurerm_network_security_rule" "all-master-to-master" {
    name = "all-master-to-master"
    priority = 100
    direction = "Inbound"
    access = "Allow"
    protocol = "Tcp"
    source_port_range = "*"
    destination_port_range = "*"
    source_address_prefix = "172.21.0.0/24"
    destination_address_prefix = "172.21.0.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    network_security_group_name = "${azurerm_network_security_group.voiceopsmaster_security_group.name}"
}

resource "azurerm_network_security_rule" "https-external-to-master-0-0-0--0" {
    name = "https-external-to-master"
    priority = 110
    direction = "Inbound"
    access = "Allow"
    protocol = "Tcp"
    source_port_range = "*"
    destination_port_range = "443"
    source_address_prefix = "*"
    destination_address_prefix = "172.21.0.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    network_security_group_name = "${azurerm_network_security_group.voiceopsmaster_security_group.name}"
}

resource "azurerm_network_security_rule" "all-node-to-master-tcp-4194" {
    name = "all-node-to-master-tcp-443"
    priority = 120
    direction = "Inbound"
    access = "Allow"
    protocol = "Tcp"
    source_port_range = "*"
    destination_port_range = "4194"
    source_address_prefix = "172.21.1.0/24"
    destination_address_prefix = "172.21.0.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    network_security_group_name = "${azurerm_network_security_group.voiceopsmaster_security_group.name}"
}

resource "azurerm_network_security_rule" "all-node-to-master-tcp-443" {
    name = "all-node-to-master-tcp-443"
    priority = 130
    direction = "Inbound"
    access = "Allow"
    protocol = "Tcp"
    source_port_range = "*"
    destination_port_range = "443"
    source_address_prefix = "172.21.1.0/24"
    destination_address_prefix = "172.21.0.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    network_security_group_name = "${azurerm_network_security_group.voiceopsmaster_security_group.name}"
}

#Security Groups for the Nodes
resource "azurerm_network_security_group" "voiceopsnode_security_group" {
    name = "voiceopsnode_security_group"
    location = "UK West"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

    tags {
        environment = "production"
    }
}


resource "azurerm_network_security_rule" "all-master-to-node" {
    name = "all-master-to-node"
    priority = 100
    direction = "Inbound"
    access = "Allow"
    protocol = "Tcp"
    source_port_range = "*"
    destination_port_range = "*"
    source_address_prefix = "172.21.0.0/24"
    destination_address_prefix = "172.21.1.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    network_security_group_name = "${azurerm_network_security_group.voiceopsnode_security_group.name}"
}

resource "azurerm_network_security_rule" "all-node-to-node" {
    name = "all-node-to-node"
    priority = 110
    direction = "Inbound"
    access = "Allow"
    protocol = "Tcp"
    source_port_range = "*"
    destination_port_range = "*"
    source_address_prefix = "172.21.1.0/24"
    destination_address_prefix = "172.21.1.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    network_security_group_name = "${azurerm_network_security_group.voiceopsnode_security_group.name}"
}

