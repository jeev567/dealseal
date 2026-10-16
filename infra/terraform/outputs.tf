output "public_ip" { value = aws_instance.app.public_ip }
output "demo_url" { value = "http://${aws_instance.app.public_ip}" }
output "instance_id" { value = aws_instance.app.id }
output "ssm_command" { value = "aws ssm start-session --target ${aws_instance.app.id} --region ${var.aws_region}" }
