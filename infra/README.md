# DealSeal demo deployment (single EC2 instance)

Terraform provisions a t3.small instance with encrypted 20 GB gp3 EBS,
Nginx, Java 21 and Systems Manager access. No RDS, NAT gateway, ALB or ECS.
The backend stores agreements, independent consent records and PayPal Sandbox
order references in SQLite. PDFs are stored on the instance's EBS disk.

## Prerequisites
- AWS CLI configured with your personal IAM identity (not root)
- Terraform >= 1.6, Java 21, Node/npm, SSH client
- AWS default VPC and public subnet in us-west-2
- An existing EC2 key pair and your current public IP /32 for SSH deployment
- Terraform permission to provision EC2, security groups and IAM roles

## Provision
```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars: set key_name and ssh_cidr = "YOUR_PUBLIC_IP/32"
terraform init
terraform plan
terraform apply
terraform output
```
Keep terraform.tfvars and Terraform state private. Public IPv4 may change
after stopping/starting the instance.

## Configure secrets on EC2
Connect via SSH with your key, then create `/etc/dealseal.env`:
```bash
sudo install -m 0600 /dev/null /etc/dealseal.env
sudo nano /etc/dealseal.env
```
Set:
```dotenv
DEALSEAL_DB_PATH=/var/lib/dealseal/dealseal.db
DEALSEAL_CONTRACTS_DIRECTORY=/var/lib/dealseal/contracts
# Add the existing app's OpenAI environment configuration.
# PAYPAL_SANDBOX_CLIENT_ID=...
# PAYPAL_SANDBOX_CLIENT_SECRET=...
```
Use `sudo chmod 600 /etc/dealseal.env`. Do not commit API keys.
Verify your actual Spring property names for OpenAI/PayPal before setting them.

## Deploy from the repository root
```bash
bash infra/deploy.sh YOUR_EC2_PUBLIC_IP ~/.ssh/YOUR_EC2_KEY.pem
```
This builds Spring Boot and React locally, securely copies the artifacts
via SSH (only your IP should be allowed), installs them, and restarts services.
See `sudo journalctl -u dealseal -n 100 --no-pager` for errors.

## HTTPS required before public use
Terraform currently exposes HTTP for bootstrap only. **Do not enter real
contract details, share consent links, or expose payment flows on HTTP.**
Set up a domain name pointing to the instance, install a TLS certificate
(e.g. Let's Encrypt), and configure Nginx HTTPS with HTTP redirect before
using this as a public demo. Approval tokens are bearer credentials and
the demo does not verify identity.

## Data / cleanup
SQLite lives at `/var/lib/dealseal/dealseal.db` and PDFs at
`/var/lib/dealseal/contracts`. These persist across application restarts,
but **not** when Terraform destroys/replaces the root volume. Back up the
database and PDF directory before replacing/destroying the instance.
Run `terraform destroy` when finished to stop AWS charges. EBS snapshots
and other retained resources incur charges separately.
