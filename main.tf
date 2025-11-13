###########################################################
# Terraform: Solr (local .solr_data folder), Redis, App
###########################################################
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
# Variables
############################
variable "solr_core" {
  type    = string
  default = "Constants"
}

variable "app_image_name" {
  type    = string
  default = "constant_tracker"
}

variable "app_image_tag" {
  type    = string
  default = "latest"
}

variable "app_port" {
  type    = number
  default = 8080
}

variable "solr_port" {
  type    = number
  default = 8983
}

variable "redis_port" {
  type    = number
  default = 6379
}

############################
# Network
############################
resource "docker_network" "appnet" {
  name = "appnet"
}

############################
# Images
############################
resource "docker_image" "app" {
  name         = "${var.app_image_name}:${var.app_image_tag}"
  keep_locally = true
}

resource "docker_image" "solr" {
  name = "solr:latest"
}

resource "docker_image" "redis" {
  name = "redis:latest"
}

############################
# Solr container (using local .solr_data)
############################
resource "docker_container" "solr" {
  name  = "solr"
  image = docker_image.solr.image_id

  command = [
    "bash",
    "-lc",
    "if [ ! -d /var/solr/data/${var.solr_core} ]; then solr-precreate ${var.solr_core}; fi && exec solr-foreground"
  ]

  networks_advanced {
    name = docker_network.appnet.name
  }

  ports {
    internal = 8983
    external = var.solr_port
  }

  # Bind-mount your project's .solr_data directory
  volumes {
    host_path      = "${path.cwd}/.solr_data"
    container_path = "/var/solr/data"
  }
}

############################
# Redis
############################
resource "docker_container" "redis" {
  name    = "redis"
  image   = docker_image.redis.image_id
  command = ["redis-server", "--appendonly", "yes"]

  ports {
    internal = 6379
    external = var.redis_port
  }

  networks_advanced {
    name = docker_network.appnet.name
  }
}

############################
# App
############################
resource "docker_container" "app" {
  name  = var.app_image_name
  image = docker_image.app.image_id

  networks_advanced {
    name = docker_network.appnet.name
  }

  ports {
    internal = 8080
    external = var.app_port
  }

  env = [
    "SERVER_PORT=8080",
    "SPRING_DATA_SOLR_HOST=http://solr:8983/solr",
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
output "app_url" {
  value = "http://localhost:${var.app_port}"
}

output "solr_url" {
  value = "http://localhost:${var.solr_port}/solr/#/"
}

output "solr_data_path" {
  value = "${path.module}/.solr_data"
}