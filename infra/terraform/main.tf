data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}
resource "aws_vpc" "dealseal" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags = { Name = "${var.project_name}-vpc" }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.dealseal.id
  cidr_block              = "10.42.1.0/24"
  map_public_ip_on_launch = true
  availability_zone       = "${var.aws_region}a"
  tags = { Name = "${var.project_name}-public" }

}

resource "aws_internet_gateway" "web" {
  vpc_id = aws_vpc.dealseal.id
  tags   = { Name = "${var.project_name}-igw" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.dealseal.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.web.id
  }
  tags = { Name = "${var.project_name}-public-rt" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "web" {
  name_prefix = "${var.project_name}-"
  description = "DealSeal demo web access"
  vpc_id = aws_vpc.dealseal.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  dynamic "ingress" {
    for_each = var.ssh_cidr == "" ? [] : [var.ssh_cidr]
    content {
      description = "Restricted SSH"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
    }
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "${var.project_name}-web" }
}
resource "aws_iam_role" "instance" {
  name_prefix = "${var.project_name}-"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
resource "aws_iam_instance_profile" "instance" {
  name_prefix = "${var.project_name}-"
  role        = aws_iam_role.instance.name
}
resource "aws_instance" "app" {
  ami = data.aws_ami.al2023.id
  instance_type = var.instance_type
  subnet_id = aws_subnet.public.id

  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.web.id]
  iam_instance_profile        = aws_iam_instance_profile.instance.name
  key_name                    = var.key_name
  user_data                   = file("${path.module}/user-data.sh")
  user_data_replace_on_change = false
  metadata_options {
    http_tokens = "required"
  }
  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    encrypted   = true
  }
  tags = {
    Name    = var.project_name
    Project = "DealSeal"
  }
}
