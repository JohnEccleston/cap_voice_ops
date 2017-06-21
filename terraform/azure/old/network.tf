provider "azure" {
  publish_settings = "${file("credentials.publishsettings")}"
}

resource "azure_virtual_network" "default" {
  name          = "voiceOps_network"
  address_space = ["172.16.11.0/23"]
  location      = "UK South"
  resource_group_name = "${azurerm_resource_group.voiceOps.name}"

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
