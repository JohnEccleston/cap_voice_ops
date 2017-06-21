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

  subnet {
    name           = "masters"
    address_prefix = "172.16.11.0/24"
  }
 
  subnet {
    name          = "nodes"
    addres_prefix = "172.16.12.0/24"
  }
  
  tags {
    environment = "Production"
  }
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
  name                = "vonim${count.id}"
  location            = "UK South"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  ip_configuration {
    name                          = "voiceOpsIpConfigMasters"
    subnet_id                     = "${azurerm_subnet.masters.id}"
    private_ip_address_allocation = "dynamic"
  }
}

resource "azurerm_network_interface" "voiceOpsNiNodes" {
  count               = 3
  name                = "vonin${count.id}"
  location            = "UK South"
  resource_group_name = "${azurerm_resource_group.voiceOpsRm.name}"

  ip_configuration {
    name                          = "voiceOpsIpConfigNodes"
    subnet_id                     = "${azurerm_subnet.nodes.id}"
    private_ip_address_allocation = "dynamic"
  }
}

resource "azurerm_managed_disk" "voiceOpsDiskMasters" {
  count                = 3
  name                 = "vomdm${count.id}"
  location             = "UK South"
  resource_group_name  = "${azurerm_resource_group.voiceOpsRm.name}"
  storage_account_type = "Standard_LRS"
  create_option        = "Empty"
  disk_size_gb         = "80"
}

resource "azurerm_managed_disk" "voiceOpsDiskNodes" {
  count                = 3
  name                 = "vomdn${count.id}"
  location             = "UK South"
  resource_group_name  = "${azurerm_resource_group.voiceOpsRm.name}"
  storage_account_type = "Standard_LRS"
  create_option        = "Empty"
  disk_size_gb         = "80"
}


