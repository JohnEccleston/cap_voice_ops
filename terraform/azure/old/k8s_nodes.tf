provider "azure" {
  publish_settings = "${file("credentials.publishsettings")}"
}

resource "azure_instance" "k8s_master1" {
  name                 = "k8s-master1"
  image                = "Ubuntu Server 14.04 LTS"
  size                 = "Basic_A1"
  storage_service_name = "yourstorage"
  location             = "UK South"
  username             = "admin"
  password             = "toBeReplaced"

  endpoint {
    name         = "SSH"
    protocol     = "tcp"
    public_port  = 22
    private_port = 22
  }
}
