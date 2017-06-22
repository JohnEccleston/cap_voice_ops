variable "masters_number" {
  type    = "string"
  default = "1"
}

variable "nodes_number" {
  type    = "string"
  default = "3"
}


provider "azurerm" {
# Creds provided by environment variables
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
    publisher = "credativ"
    offer     = "Debian"
    sku       = "9"
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
    publisher = "credativ"
    offer     = "Debian"
    sku       = "9"
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
