provider "azure" {
# Need to work out creds
}

# Create resource group

resource "azurerm_resource_group" "voiceOpsRm" {
  name     = "voiceOpsResourceGroup"
  location = "UK South"

  tags {
    environment = "Production"
  }
}

# Network Setup

resource "azurerm_virtual_network" "voiceOpsVn" {
  name          = "voiceOpsNetwork"
  address_space = ["172.16.11.0/23"]
  location      = "UK South"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  tags {
    environment = "Production"
  }
}

resource "azurerm_subnet" voiceOpsSnMasters {
    name           = "masters"
    address_prefix = "172.16.11.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    virtual_network_name = "${azurerm_virtual_network.voiceOpsVn.name}"
}

resource "azurerm_subnet" voiceOpsSnNodes {
    name          = "nodes"
    address_prefix = "172.16.12.0/24"
    resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"
    virtual_network_name = "${azurerm_virtual_network.voiceOpsVn.name}"
}
  
# Create Availability Set for Masters

resource "azurerm_availability_set" "voiceOpsAsMasters" {
  name                = "voiceOpsMastersAs"
  location            = "UK South"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  tags {
    environment = "Production"
  }
}

# Create Availability Set for Nodes

resource "azurerm_availability_set" "voiceOpsAsNodes" {
  name                = "voiceOpsNodesAs"
  location            = "UK South"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  tags {
    environment = "Production"
  }
}

resource "azurerm_network_interface" "voiceOpsNiMasters" {
  count               = 3
  name                = "vonim${count.index}"
  location            = "UK South"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  ip_configuration {
    name                          = "voiceOpsIpConfigMasters"
    subnet_id                     = "${azurerm_subnet.voiceOpsSnMasters.id}"
    private_ip_address_allocation = "dynamic"
  }
}

resource "azurerm_network_interface" "voiceOpsNiNodes" {
  count               = 3
  name                = "vonin${count.index}"
  location            = "UK South"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  ip_configuration {
    name                          = "voiceOpsIpConfigNodes"
    subnet_id                     = "${azurerm_subnet.voiceOpsSnNodes.id}"
    private_ip_address_allocation = "dynamic"
  }
}

resource "azurerm_virtual_machine" "voiceOpsMasters" {
  count                 = 3
  name                  = "vomvm${count.index}"
  location              = "UK South"
  resource_group_name   = "${azurerm_resource_group.voiceOpsRm.name}"
  network_interface_ids = ["${azurerm_network_interface.voiceOpsNiMasters.count.index.id}"]
  vm_size               = "Standard_DS1_v2"

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
    admin_username = "admin"
    admin_password = "changeme"
  }

  os_profile_linux_config {
    disable_password_authentication = false
  }

  tags {
    environment = "production"
  }
}

resource "azurerm_virtual_machine" "voiceOpsNodes" {
  count                 = 3
  name                  = "vonvm${count.index}"
  location              = "UK South"
  resource_group_name   = "${azurerm_resource_group.voiceOpsRm.name}"
  network_interface_ids = ["${azurerm_network_interface.voiceOpsNiNodes.count.index.id}"]
  vm_size               = "Standard_DS1_v2"

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
    admin_username = "admin"
    admin_password = "changeme"
  }

  os_profile_linux_config {
    disable_password_authentication = false
  }

  tags {
    environment = "production"
  }
}
