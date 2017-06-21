provider "azure" {
}

resource "azurerm_resource_group" "voiceOps" {
  name     = "voiceOpsResourceGroup"
  location = "UK South"

  tags {
    environment = "Production"
  }
}

resource "azure_instance" "k8s-master1" {
  name                 = "k8s-master1"
  image                = "Debian 9 \"Stretch\""
  size                 = "Basic_A1"
  storage_service_name = "yourstorage"
  location             = "UK South"
  resource_group_name = "${azurerm_resource_group.voiceOps.name}"
  username             = "admin"
  password             = "toBeReplaced"

  endpoint {
    name         = "SSH"
    protocol     = "tcp"
    public_port  = 22
    private_port = 22
  }
}
