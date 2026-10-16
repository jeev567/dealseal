# DealSeal demo AWS infrastructure

Single EC2 t3.small, encrypted 20 GB gp3 root EBS, Nginx and Java 21.
No RDS, NAT gateway, ALB or ECS. Uses the default VPC and a default public subnet.
The default public IPv4 address changes when the instance stops and starts.

## Prerequisites
- Terraform >= 1.6
- AWS credentials configured (e.g. `aws configure`)
- Default VPC and public subnet in selected region
- EC2/SSM permissions to create resources

## Provision
```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
terraform apply
terraform output
```
Terraform state contains infrastructure information. Do not commit state or credentials.
Use the generated SSM command to access the server; SSH is disabled by default.

## Deploy
Build the Spring Boot jar and React production assets locally, then use
`infra/deploy.sh <instance-id> <region>` from the repository root.
Requires AWS CLI, SSM access, and an S3 bucket you own for temporary artifact transfer.
Set `DEALSEAL_DEPLOY_BUCKET` in your environment. This script uploads artifacts
to that bucket and deploys them through SSM, then removes the uploaded artifacts.
Store application API keys in `/etc/dealseal.env` on the instance (mode 0600).
Never commit credentials.

## HTTPS and demo safety
The initial output URL is HTTP only. Do not send real approval links, private
contracts or credentials over HTTP. Configure a DNS name and HTTPS with
Let's Encrypt before publicly demonstrating sensitive workflows. The app
currently uses bearer approval links without identity verification; keep
the demo data fictional.

## Costs / cleanup
On-demand EC2, EBS, public IPv4 and data transfer incur charges. Check regional
pricing. `terraform destroy` removes the instance and its root disk, including
SQLite/PDF data; back up any data you want to keep before destroying it.
