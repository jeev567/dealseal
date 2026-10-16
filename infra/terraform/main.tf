data "aws_ami" "al2023" {
  most_recent = true
  owners = ["amazon"]
  filter { name = "name"; values = ["al2023-ami-2023.*-x86_64"] }
  filter { name = "architecture"; values = ["x86_64"] }
  filter { name = "virtualization-type"; values = ["hvm"] }
}
data "aws_vpc" "default" { default = true }
data "aws_subnets" "default" {
  filter { name = "vpc-id"; values = [data.aws_vpc.default.id] }
  filter { name = "default-for-az"; values = ["true"] }
}
resource "aws_security_group" "web" {
  name_prefix = "${var.project_name}-"
  description = "DealSeal demo web access"
  vpc_id = data.aws_vpc.default.id
  ingress { description = "HTTP"; from_port = 80; to_port = 80; protocol = "tcp"; cidr_blocks = ["0.0.0.0/0"] }
  ingress { description = "HTTPS"; from_port = 443; to_port = 443; protocol = "tcp"; cidr_blocks = ["0.0.0.0/0"] }
  dynamic "ingress" {
    for_each = var.ssh_cidr == "" ? [] : [var.ssh_cidr]
    content { description = "Restricted SSH"; from_port = 22; to_port = 22; protocol = "tcp"; cidr_blocks = [ingress.value] }
  }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
  tags = { Name = "${var.project_name}-web" }
}
resource "aws_iam_role" "instance" {
  name_prefix = "${var.project_name}-"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}
resource "aws_iam_role_policy_attachment" "ssm" {
  role = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
resource "aws_iam_instance_profile" "instance" {
  name_prefix = "${var.project_name}-"
  role = aws_iam_role.instance.name
}
resource "aws_instance" "app" {
  ami = data.aws_ami.al2023.id
  instance_type = var.instance_type
  subnet_id = sort(data.aws_subnets.default.ids)[0]
  associate_public_ip_address = true
  vpc_security_group_ids = [aws_security_group.web.id]
  iam_instance_profile = aws_iam_instance_profile.instance.name
  key_name = var.key_name
  user_data = file("${path.module}/user-data.sh")
  user_data_replace_on_change = false
  metadata_options { http_tokens = "required" }
  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    encrypted = true
  }
  tags = { Name = var.project_name; Project = "DealSeal" }
}
