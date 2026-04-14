.PHONY: demo demo-down build logs ps

demo:
	@chmod +x demo/demo.sh && ./demo/demo.sh

demo-down:
	@docker compose -f infra/docker-compose.yml down -v

build:
	@docker compose -f infra/docker-compose.yml build

logs:
	@docker compose -f infra/docker-compose.yml logs -f

ps:
	@docker compose -f infra/docker-compose.yml ps
