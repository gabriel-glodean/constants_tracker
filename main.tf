############################################################
# Terraform: copy ./solr -> host folder, then run Solr/Redis/App
############################################################
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = ">= 3.0.2"
    }
  }
}

provider "docker" {}

############################
# Variables (tweak as needed)
############################
# Where to copy your project ./solr to on the HOST (final mount source)
# Use forward slashes even on Windows (Docker Desktop accepts them).
variable "solr_host_path" {
  type    = string
  # Windows example:
  default = "C:/dev/solr_data"
  # macOS/Linux example: "/Users/you/dev/solr_data"
}

# Set to "windows" or "unix"
variable "host_os" {
  type    = string
  default = "windows"
  validation {
    condition     = contains(["windows", "unix"], var.host_os)
    error_message = "host_os must be 'windows' or 'unix'."
  }
}

variable "solr_core"      {
  type = string
  default = "mycore"
}
variable "app_image_name" {
  type = string
  default = "constant_tracker"
}
variable "app_image_tag"  {
  type = string
  default = "latest"
}
variable "app_port"       {
  type = number
  default = 8080
}
variable "solr_port"      {
  type = number
  default = 8983
}

############################
# Network
############################
resource "docker_network" "appnet" {
  name = "appnet"
}

############################
# Build/pull images
############################
# Build your app from the local Dockerfile
resource "docker_image" "app" {
  name = "${var.app_image_name}:${var.app_image_tag}"
  build {
    context    = "."
    dockerfile = "Dockerfile"
  }
  keep_locally = true
}

resource "docker_image" "solr"  { name = "solr:9" }
resource "docker_image" "redis" { name = "redis:7-alpine" }

############################
# Copy ./solr -> var.solr_host_path (option #3)
############################
# Re-run copy when any file under ./solr changes
locals {
  solr_src = "${path.module}/solr"
}

# Unix copy (rsync); enabled when host_os == "unix"
resource "null_resource" "copy_solr_unix" {
  count = var.host_os == "unix" ? 1 : 0

  triggers = {
    src_hash = sha1(join("", fileset(local.solr_src, "")))
  }

  provisioner "local-exec" {
    interpreter = ["/bin/sh", "-c"]
    command     = <<-EOC
      mkdir -p "${var.solr_host_path}"
      rsync -a "${local.solr_src}/" "${var.solr_host_path}/"
    EOC
  }
}

# Windows copy (PowerShell + robocopy); enabled when host_os == "windows"
resource "null_resource" "copy_solr_win" {
  count = var.host_os == "windows" ? 1 : 0

  triggers = {
    src_hash = sha1(join("", fileset(local.solr_src, "")))
  }

  provisioner "local-exec" {
    command = <<-EOC
      powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "New-Item -ItemType Directory -Force -Path '${var.solr_host_path}' | Out-Null; ^
         robocopy '${local.solr_src}' '${var.solr_host_path}' /E /NFL /NDL /NJH /NJS /NC /NS; ^
         if ($LASTEXITCODE -ge 8) { exit $LASTEXITCODE }"
    EOC
  }
}

############################
# Containers
############################
resource "docker_container" "solr" {
  name  = "solr"
  image = docker_image.solr.image_id

  # Ensure copy finished
  depends_on = [
    null_resource.copy_solr_unix,
    null_resource.copy_solr_win
  ]

  # Have Solr ensure the core exists (pre-create if missing)
  # (Solr will also load any core present under /var/solr/data/<core>)
  command = ["bash", "-lc", "if [ ! -d /var/solr/data/${var.solr_core} ]; then solr-precreate ${var.solr_core}; fi && exec solr-foreground"]

  networks_advanced { name = docker_network.appnet.name }

  ports {
    internal = 8983
    external = var.solr_port
  }

  # Mount the copied host folder as Solr's core root
  volumes {
    host_path      = var.solr_host_path
    container_path = "/var/solr/data"
  }
}

resource "docker_container" "redis" {
  name    = "redis"
  image   = docker_image.redis.image_id
  command = ["redis-server", "--appendonly", "yes"]

  networks_advanced { name = docker_network.appnet.name }
}

resource "docker_container" "app" {
  name  = var.app_image_name
  image = docker_image.app.image_id

  networks_advanced { name = docker_network.appnet.name }

  ports {
    internal = 8080
    external = var.app_port
  }

  env = [
    "SERVER_PORT=8080",
    # Solr base URL for Spring Data Solr / SolrJ
    "SPRING_DATA_SOLR_HOST=http://solr:8983/solr",
    # Redis wiring
    "REDIS_HOST=redis",
    "REDIS_PORT=6379",
  ]

  depends_on = [
    docker_container.solr,
    docker_container.redis
  ]
}

############################
# Outputs
############################
output "app_url"  { value = "http://localhost:${var.app_port}" }
output "solr_url" { value = "http://localhost:${var.solr_port}/solr/#/" }
output "mounted_solr_host_path" { value = var.solr_host_path}