#!/bin/bash
set -euxo pipefail
dnf update -y
dnf install -y java-21-amazon-corretto-headless nginx git tar gzip awscli
systemctl enable --now amazon-ssm-agent
mkdir -p /opt/dealseal /var/lib/dealseal/contracts
id dealseal || useradd --system --home /opt/dealseal --shell /sbin/nologin dealseal
chown -R dealseal:dealseal /opt/dealseal /var/lib/dealseal
cat >/etc/nginx/conf.d/dealseal.conf <<'NGINX'
server {
    listen 80 default_server;
    server_name _;
    root /opt/dealseal/frontend;
    index index.html;
    client_max_body_size 6m;
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location / { try_files $uri $uri/ /index.html; }
}
NGINX
cat >/etc/systemd/system/dealseal.service <<'SERVICE'
[Unit]
Description=DealSeal Spring Boot demo
After=network-online.target
Wants=network-online.target
[Service]
User=dealseal
WorkingDirectory=/opt/dealseal
EnvironmentFile=-/etc/dealseal.env
Environment=DEALSEAL_CONTRACTS_DIRECTORY=/var/lib/dealseal/contracts
ExecStart=/usr/bin/java -Xms128m -Xmx900m -jar /opt/dealseal/app.jar
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/var/lib/dealseal
[Install]
WantedBy=multi-user.target
SERVICE
systemctl daemon-reload
systemctl enable dealseal nginx
systemctl restart nginx
