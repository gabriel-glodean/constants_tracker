###########################################################
# Terraform: Docker Compose deployment to existing server
###########################################################
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    null = {
      source  = "hashicorp/null"
      version = ">= 3.0.0"
    }
  }
}

############################
# Variables
############################
variable "server_ip" {
  type        = string
  description = "Public IP address of the target server"
}

variable "ssh_private_key_path" {
  type        = string
  default     = "~/.ssh/id_rsa"
  description = "Local path to the SSH private key used by provisioners"
}

variable "ssh_user" {
  type        = string
  default     = "root"
  description = "SSH user on the target server"
}

# ---- Application images ----
variable "app_image" {
  type    = string
  default = "ghcr.io/gabriel-glodean/constant-tracker-app:latest"
}

variable "ui_image" {
  type    = string
  default = "ghcr.io/gabriel-glodean/constant-tracker-ui:latest"
}

# ---- Secrets / environment ----
variable "postgres_user" {
  type      = string
  sensitive = true
}

variable "postgres_password" {
  type      = string
  sensitive = true
}

variable "spring_r2dbc_username" {
  type      = string
  sensitive = true
}

variable "spring_r2dbc_password" {
  type      = string
  sensitive = true
}

variable "spring_flyway_user" {
  type      = string
  sensitive = true
}

variable "spring_flyway_password" {
  type      = string
  sensitive = true
}

variable "cors_allowed_origins" {
  type        = string
  default     = "*"
  description = "Comma-separated list of allowed CORS origins for the app"
}

############################
# Deploy via docker compose
############################
resource "null_resource" "deploy" {
  # Re-deploy whenever the image tags or demo JARs change
  triggers = {
    app_image   = var.app_image
    ui_image    = var.ui_image
    v1_jar_hash = filemd5("${path.module}/demo-crud-server/build/libs/demo-crud-server-0.1.0-SNAPSHOT.jar")
    v2_jar_hash = filemd5("${path.module}/demo-crud-server-v2/build/libs/demo-crud-server-v2-0.2.0-SNAPSHOT.jar")
  }

  connection {
    type        = "ssh"
    host        = var.server_ip
    user        = var.ssh_user
    private_key = file(var.ssh_private_key_path)
    timeout     = "5m"
  }

  # 1. Ensure directories exist
  provisioner "remote-exec" {
    inline = [
      "mkdir -p /opt/constant-tracker/constant-tracker-app/solr",
      "mkdir -p /opt/constant-tracker/.solr_data",
      "mkdir -p /opt/constant-tracker/.postgres_data",
      "mkdir -p /opt/constant-tracker/demo-crud-server/build/libs",
      "mkdir -p /opt/constant-tracker/demo-crud-server-v2/build/libs",
    ]
  }

  # 2. Copy docker-compose.yml
  provisioner "file" {
    source      = "${path.module}/docker-compose.yml"
    destination = "/opt/constant-tracker/docker-compose.yml"
  }

  # 3. Copy Solr config
  provisioner "file" {
    source      = "${path.module}/constant-tracker-app/solr/"
    destination = "/opt/constant-tracker/constant-tracker-app/solr"
  }

  # 4. Copy demo JAR v1
  provisioner "file" {
    source      = "${path.module}/demo-crud-server/build/libs/demo-crud-server-0.1.0-SNAPSHOT.jar"
    destination = "/opt/constant-tracker/demo-crud-server/build/libs/demo-crud-server-0.1.0-SNAPSHOT.jar"
  }

  # 5. Copy demo JAR v2
  provisioner "file" {
    source      = "${path.module}/demo-crud-server-v2/build/libs/demo-crud-server-v2-0.2.0-SNAPSHOT.jar"
    destination = "/opt/constant-tracker/demo-crud-server-v2/build/libs/demo-crud-server-v2-0.2.0-SNAPSHOT.jar"
  }

  # 6. Write .env file
  provisioner "file" {
    content     = <<-ENV
      APP_IMAGE=${var.app_image}
      UI_IMAGE=${var.ui_image}

      POSTGRES_USER=${var.postgres_user}
      POSTGRES_PASSWORD=${var.postgres_password}

      SPRING_R2DBC_USERNAME=${var.spring_r2dbc_username}
      SPRING_R2DBC_PASSWORD=${var.spring_r2dbc_password}

      SPRING_FLYWAY_USER=${var.spring_flyway_user}
      SPRING_FLYWAY_PASSWORD=${var.spring_flyway_password}

      CORS_ALLOWED_ORIGINS=${var.cors_allowed_origins}

      POSTGRES_HOST_PATH=/opt/constant-tracker/.postgres_data
      SOLR_HOST_PATH=/opt/constant-tracker/.solr_data
    ENV
    destination = "/opt/constant-tracker/.env"
  }

  # 7. Tear down any stale containers, pull latest images, bring everything up fresh
  provisioner "remote-exec" {
    inline = [
      "cd /opt/constant-tracker",
      "echo 'Tearing down existing stack...'",
      "docker compose down --remove-orphans --timeout 30 || true",
      "echo 'Pulling latest images...'",
      "docker compose pull",
      "echo 'Starting services...'",
      "docker compose up -d",
      "echo 'Deployment complete.'",
    ]
  }

  # 8. Clear stale demo data and seed — only when JARs have changed or never seeded
  # --no-deps prevents compose run from trying to recreate already-running dependency containers
  provisioner "remote-exec" {
    inline = [
      "cd /opt/constant-tracker",
      "CURRENT_HASH='${self.triggers.v1_jar_hash}_${self.triggers.v2_jar_hash}'",
      "STORED_HASH=$(cat .seeded 2>/dev/null || echo '')",
      "if [ \"$CURRENT_HASH\" != \"$STORED_HASH\" ]; then",
      "  echo 'JAR hashes differ or first run — clearing existing demo data...'",
      "  docker compose --profile clear-demo run --rm --no-deps clear-demo || true",
      "  echo 'Seeding demo data...'",
      "  docker compose --profile seed run --rm --no-deps seed",
      "  echo \"$CURRENT_HASH\" > .seeded",
      "  echo 'Seeding complete.'",
      "else",
      "  echo 'JARs unchanged — skipping clear and seed.'",
      "fi",
    ]
  }
}

############################
# Outputs
############################

output "ui_url" {
  description = "Search UI endpoint"
  value       = "http://${var.server_ip}:5173"
}