# Makefile for common tasks
APP_NAME=demo
JAR=target/demo-0.0.1-SNAPSHOT.jar
PORT?=8082

.PHONY: build run clean docker-build docker-run test

build:
	mvn -DskipTests package

run: build
	java -jar $(JAR) --server.port=$(PORT)

clean:
	mvn clean

docker-build:
	docker build -t $(APP_NAME):latest .

# Run the container exposing the port
docker-run:
	docker run --rm -p $(PORT):8080 --name $(APP_NAME) $(APP_NAME):latest

test:
	mvn test

# quick image clean
docker-clean:
	docker image rm -f $(APP_NAME):latest || true

