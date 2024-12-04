terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
}

resource "aws_vpc" "iot-surv-vpc" {
    cidr_block = "10.0.0.0/16"
    tags = {
      Name = "iot-surveillance-system"
    }
}

resource "aws_internet_gateway" "gw" {
    vpc_id = aws_vpc.iot-surv-vpc.id
}

resource "aws_route_table" "iot-route-table" {
  vpc_id = aws_vpc.iot-surv-vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.gw.id
  }

  tags = {
    Name = "iot-public-subnet"
  }
}

resource "aws_subnet" "public-subnet" {
    vpc_id = aws_vpc.iot-surv-vpc.id
    cidr_block = "10.0.1.0/24"
    availability_zone = "us-east-1a"
    map_public_ip_on_launch = true

    tags = {
      Name = "public-subnet"
    }
}

resource "aws_subnet" "public-subnet2" {
    vpc_id = aws_vpc.iot-surv-vpc.id
    cidr_block = "10.0.2.0/24"
    availability_zone = "us-east-1b"
    map_public_ip_on_launch = true

    tags = {
      Name = "public-subnet2"
    }
}

resource "aws_route_table_association" "a" {
    subnet_id = aws_subnet.public-subnet.id
    route_table_id = aws_route_table.iot-route-table.id
}

resource "aws_route_table_association" "a2" {
    subnet_id = aws_subnet.public-subnet2.id
    route_table_id = aws_route_table.iot-route-table.id
}

resource "aws_security_group" "IOTCamerasLBSecurityGroup" {
    name = "IOTCamerasLBSecurityGroup" 
    description = "IOTCamerasLBSecurityGroup"
    vpc_id = aws_vpc.iot-surv-vpc.id

    ingress {
        description = "allow-http-80"
        from_port = 80 
        to_port = 80 
        protocol = "tcp"
        cidr_blocks = ["0.0.0.0/0"]
    }

    egress {
        from_port = 0 
        to_port = 0 
        protocol = "-1"
        cidr_blocks = ["0.0.0.0/0"]
    }

    tags = {
      Name = "IOTCamerasLBSecurityGroup"
    }
}

resource "aws_lb" "iot-cameras-alb" {
 name               = "IOTCamerasLB"
 internal           = false
 load_balancer_type = "application"
 security_groups    = [aws_security_group.IOTCamerasLBSecurityGroup.id]
 subnets            = [aws_subnet.public-subnet.id, aws_subnet.public-subnet2.id]

 tags = {
   Name = "IOTCamerasLB"
 }
}

resource "aws_lb_target_group" "iot-cameras-tg" {
 name        = "IOTCamerasTargetGroup"
 port        = 8080
 protocol    = "HTTP"
 target_type = "ip"
 vpc_id      = aws_vpc.iot-surv-vpc.id

 health_check {
   path = "/"
 }
}

resource "aws_lb_listener" "ecs_alb_listener" {
 load_balancer_arn = aws_lb.iot-cameras-alb.arn
 port              = 80
 protocol          = "HTTP"

 default_action {
   type             = "forward"
   target_group_arn = aws_lb_target_group.iot-cameras-tg.arn
 }
}

resource "aws_ecs_cluster" "iot-cameras-cluster" {
  name = "iot-cameras-cluster"
}

resource "aws_ecs_task_definition" "iot-camera" {
  family                = jsondecode(file("${path.module}/src/task-definition.json")).family
  container_definitions = jsonencode(jsondecode(file("${path.module}/src/task-definition.json")).containerDefinitions)
  task_role_arn         = jsondecode(file("${path.module}/src/task-definition.json")).taskRoleArn
  execution_role_arn    = jsondecode(file("${path.module}/src/task-definition.json")).executionRoleArn
  network_mode          = jsondecode(file("${path.module}/src/task-definition.json")).networkMode
  requires_compatibilities = jsondecode(file("${path.module}/src/task-definition.json")).requiresCompatibilities
  cpu                   = jsondecode(file("${path.module}/src/task-definition.json")).cpu
  memory                = jsondecode(file("${path.module}/src/task-definition.json")).memory
  runtime_platform {
    cpu_architecture     = jsondecode(file("${path.module}/src/task-definition.json")).runtimePlatform.cpuArchitecture
    operating_system_family = jsondecode(file("${path.module}/src/task-definition.json")).runtimePlatform.operatingSystemFamily
  }
}


resource "aws_security_group" "IOTCamerasServiceSecurityGroup" {
    name = "IOTCamerasServiceSecurityGroup" 
    description = "IOTCamerasServiceSecurityGroup"
    vpc_id = aws_vpc.iot-surv-vpc.id

    ingress {
        description = "allow-traffic-from-lb"
        from_port = 0
        to_port = 65535
        protocol = "tcp"
        security_groups = [aws_security_group.IOTCamerasLBSecurityGroup.id]
    }

    egress {
        from_port = 0 
        to_port = 0 
        protocol = "-1"
        cidr_blocks = ["0.0.0.0/0"]
    }

    tags = {
      Name = "IOTCamerasLBSecurityGroup"
    }
}

resource "aws_ecs_service" "sample_service" {
  name            = "IOTCamerasService"
  cluster         = aws_ecs_cluster.iot-cameras-cluster.id
  task_definition = aws_ecs_task_definition.iot-camera.arn
  desired_count   = 5
  
  launch_type = "FARGATE"

  network_configuration {
    subnets = [aws_subnet.public-subnet.id, aws_subnet.public-subnet2.id]
    security_groups = [aws_security_group.IOTCamerasServiceSecurityGroup.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.iot-cameras-tg.arn
    container_name = "iot-camera"
    container_port = 8080
  }

  depends_on = [ 
    aws_lb_listener.ecs_alb_listener
  ]
}