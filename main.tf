###########################################################
# Terraform: k3s deployment to existing server
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

variable "ingress_host" {
  type        = string
  default     = "tracker.gg-dev.org"
  description = "Hostname for the Traefik ingress rule"
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
  default     = "https://tracker.gg-dev.org"
  description = "Comma-separated list of allowed CORS origins for the app"
}

variable "auth_enabled" {
  type        = bool
  default     = true
  description = "Set to false to disable JWT enforcement (dev/test only)"
}

variable "auth_jwt_secret" {
  type        = string
  sensitive   = true
  description = "Base64-encoded HS256 secret used to sign JWTs (min 32 chars before encoding)"
}

variable "demo_username" {
  type        = string
  default     = "demo"
  description = "Username of the seeded demo account"
}

variable "demo_password" {
  type        = string
  sensitive   = true
  description = "Plain-text password for the demo account (BCrypt-hashed inside Postgres via pgcrypto)"
}

############################
# Deploy via k3s
############################
resource "null_resource" "deploy" {
  triggers = {
    v1_jar_hash = filemd5("${path.module}/demo-crud-server/build/libs/demo-crud-server-0.1.0-SNAPSHOT.jar")
    v2_jar_hash = filemd5("${path.module}/demo-crud-server-v2/build/libs/demo-crud-server-v2-0.2.0-SNAPSHOT.jar")
    k8s_hash    = sha256(join("", [for f in sort(fileset("${path.module}/k8s", "*.yml")) : filemd5("${path.module}/k8s/${f}")]))
    always_run  = timestamp()
  }

  connection {
    type        = "ssh"
    host        = var.server_ip
    user        = var.ssh_user
    private_key = file(var.ssh_private_key_path)
    timeout     = "5m"
  }

  # 0. Install k3s if not already present
  provisioner "remote-exec" {
    inline = [
      "if ! command -v k3s > /dev/null 2>&1; then",
      "  echo 'Installing k3s...'",
      "  curl -sfL https://get.k3s.io | sh -",
      "  echo 'k3s installed.'",
      "else",
      "  echo 'k3s already installed, skipping.'",
      "fi",
      "until kubectl get nodes > /dev/null 2>&1; do echo 'Waiting for k3s to be ready...'; sleep 3; done",
      "echo 'k3s is ready.'",
    ]
  }

  # 1. Ensure directories exist
  provisioner "remote-exec" {
    inline = [
      "mkdir -p /opt/constant-tracker/k8s",
      "mkdir -p /opt/constant-tracker/solr",
      "mkdir -p /opt/constant-tracker/demo-crud-server/build/libs",
      "mkdir -p /opt/constant-tracker/demo-crud-server-v2/build/libs",
    ]
  }

  # 2. Copy k8s manifests
  provisioner "file" {
    source      = "${path.module}/k8s/"
    destination = "/opt/constant-tracker/k8s"
  }

  # 3. Copy Solr config files (used for solr-init-config ConfigMap)
  provisioner "file" {
    source      = "${path.module}/constant-tracker-app/solr/"
    destination = "/opt/constant-tracker/solr"
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

  # 6. Apply namespace and base configs; populate solr-init-config BEFORE solr.yml
  #    so the Solr pod always starts with the real schema/solrconfig mounted.
  provisioner "remote-exec" {
    inline = [
      "kubectl apply -f /opt/constant-tracker/k8s/namespace.yml",
      "kubectl apply -f /opt/constant-tracker/k8s/configs.yml",
      "kubectl create configmap solr-init-config --namespace=constant-tracker --from-file=managed-schema.xml=/opt/constant-tracker/solr/managed-schema.xml --from-file=solrconfig.xml=/opt/constant-tracker/solr/solrconfig.xml --dry-run=client -o yaml | kubectl apply -f -",
      "kubectl apply -f /opt/constant-tracker/k8s/redis.yml",
      "kubectl apply -f /opt/constant-tracker/k8s/postgres.yml",
      "kubectl apply -f /opt/constant-tracker/k8s/solr.yml",
      "kubectl apply -f /opt/constant-tracker/k8s/app.yml",
      "kubectl apply -f /opt/constant-tracker/k8s/ui.yml",
    ]
  }

  # 7. Create/update secrets BEFORE rollout so pods never start with missing keys.
  provisioner "remote-exec" {
    inline = [
      "kubectl create secret generic postgres-secret --namespace=constant-tracker --from-literal=POSTGRES_USER='${var.postgres_user}' --from-literal=POSTGRES_PASSWORD='${var.postgres_password}' --dry-run=client -o yaml | kubectl apply -f -",
      "kubectl create secret generic app-secret --namespace=constant-tracker --from-literal=SPRING_R2DBC_USERNAME='${var.spring_r2dbc_username}' --from-literal=SPRING_R2DBC_PASSWORD='${var.spring_r2dbc_password}' --from-literal=SPRING_FLYWAY_USER='${var.spring_flyway_user}' --from-literal=SPRING_FLYWAY_PASSWORD='${var.spring_flyway_password}' --from-literal=CONSTANTS_AUTH_JWT_SECRET='${var.auth_jwt_secret}' --dry-run=client -o yaml | kubectl apply -f -",
      "kubectl create secret generic demo-secret --namespace=constant-tracker --from-literal=DEMO_USERNAME=$(printf '%s' '${var.demo_username}') --from-literal=DEMO_PASSWORD=$(printf '%s' '${var.demo_password}') --dry-run=client -o yaml | kubectl apply -f -",
    ]
  }

  # 8. Patch app-config with runtime values BEFORE rollout.
  provisioner "remote-exec" {
    inline = [
      "kubectl create configmap app-config --namespace=constant-tracker --from-literal=CORS_ALLOWED_ORIGINS='${var.cors_allowed_origins}' --from-literal=SPRING_R2DBC_URL='r2dbc:postgresql://postgres:5432/constant_tracker' --from-literal=SPRING_FLYWAY_URL='jdbc:postgresql://postgres:5432/constant_tracker' --from-literal=SPRING_DATA_REDIS_HOST='redis' --from-literal=SPRING_DATA_REDIS_PORT='6379' --from-literal=CONSTANTS_SOLR_URL='http://solr:8983/solr/' --from-literal=SPRING_DATA_SOLR_HOST='http://solr:8983/solr' --from-literal=SERVER_PORT='8080' --from-literal=CONSTANTS_AUTH_ENABLED='${var.auth_enabled}' --dry-run=client -o yaml | kubectl apply -f -",
    ]
  }

  # 9. Force app and UI to pull latest image — secrets and configmap are guaranteed
  #    to exist at this point so pods start cleanly.
  provisioner "remote-exec" {
    inline = [
      "kubectl rollout restart deployment/app --namespace=constant-tracker",
      "kubectl rollout restart deployment/ui --namespace=constant-tracker",
      "kubectl rollout status deployment/app --namespace=constant-tracker --timeout=180s",
      "kubectl rollout status deployment/ui --namespace=constant-tracker --timeout=120s",
    ]
  }

  # 10. Apply ingress with substituted host
  provisioner "remote-exec" {
    inline = [
      "sed 's|$${INGRESS_HOST}|${var.ingress_host}|g' /opt/constant-tracker/k8s/ingress.yml | kubectl apply -f -",
    ]
  }

  # 11. Seed demo data — only when JARs have changed or never seeded.
  #     When auth is enabled the demo auth user is created FIRST so the
  #     seed-job can authenticate against the app API before uploading JARs.
  provisioner "remote-exec" {
    inline = [
      "CURRENT_HASH='${self.triggers.v1_jar_hash}_${self.triggers.v2_jar_hash}'",
      "STORED_HASH=$(cat /opt/constant-tracker/.seeded 2>/dev/null || echo '')",
      "if [ \"$CURRENT_HASH\" != \"$STORED_HASH\" ]; then",
      "  echo 'JAR hashes differ or first run — clearing existing demo data...'",
      "  kubectl delete job clear-demo-job --namespace=constant-tracker --ignore-not-found=true",
      "  kubectl apply -f /opt/constant-tracker/k8s/jobs.yml --selector=app=clear-demo-job",
      "  kubectl wait --for=condition=complete job/clear-demo-job --namespace=constant-tracker --timeout=120s || true",
      "  if [ '${var.auth_enabled}' = 'true' ]; then",
      "    echo 'Auth is enabled — seeding demo auth user before uploading JARs...'",
      "    kubectl delete job seed-auth-user-job --namespace=constant-tracker --ignore-not-found=true",
      "    kubectl apply -f /opt/constant-tracker/k8s/jobs.yml --selector=app=seed-auth-user-job",
      "    kubectl wait --for=condition=complete job/seed-auth-user-job --namespace=constant-tracker --timeout=120s",
      "    echo 'Demo auth user ready.'",
      "  else",
      "    echo 'Auth is disabled — skipping demo user seed.'",
      "  fi",
      "  echo 'Seeding demo data...'",
      "  kubectl delete job seed-job --namespace=constant-tracker --ignore-not-found=true",
      "  kubectl apply -f /opt/constant-tracker/k8s/jobs.yml --selector=app=seed-job",
      "  kubectl wait --for=condition=complete job/seed-job --namespace=constant-tracker --timeout=300s",
      "  echo \"$CURRENT_HASH\" > /opt/constant-tracker/.seeded",
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
  value       = "https://${var.ingress_host}"
}