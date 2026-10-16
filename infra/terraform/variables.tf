variable "aws_region" {
  type = string
  default = "us-west-2"
}
variable "project_name" {
  type = string
  default = "dealseal-demo"
}
variable "instance_type" {
  type = string
  default = "t3.small"
}
variable "root_volume_gb" {
  type = number
  default = 20
}
variable "ssh_cidr" {
  description = "Your public IP /32 for optional SSH access. Leave empty to use SSM only."
  type = string
  default = ""
}
variable "key_name" {
  description = "Optional existing EC2 key pair name; not required when using SSM."
  type = string
  default = null
}
variable "domain_name" {
  description = "Optional DNS hostname for HTTPS; configure DNS and TLS separately."
  type = string
  default = ""
}
