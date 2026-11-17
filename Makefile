.PHONY: up down build restart logs probe catalog matrix debloat-dry-run debloat restore-pkg harden-dry-run harden vbmeta overlay test test-jvm test-blocks qa audit

PORT ?= 8245
DNS ?= adguard
PKG ?= com.samsung.android.rubin.app
MODEL ?=

up:
	python3 scripts/windows_usb_bridge.py --out data/host_usb_probe.json
	docker compose up -d --build

down:
	docker compose down

build:
	docker compose build

restart:
	python3 scripts/windows_usb_bridge.py --out data/host_usb_probe.json
	docker compose restart

logs:
	docker compose logs -f --tail=100

probe:
	python3 scripts/windows_usb_bridge.py --out data/host_usb_probe.json
	@if [ -n "$(MODEL)" ]; then \
		docker exec android-privacy-studio python3 cli.py probe --model "$(MODEL)"; \
	else \
		docker exec android-privacy-studio python3 cli.py probe; \
	fi

catalog:
	docker exec android-privacy-studio python3 cli.py catalog

matrix:
	docker exec android-privacy-studio python3 cli.py catalog --matrix

debloat-dry-run:
	docker exec android-privacy-studio python3 cli.py debloat

debloat:
	docker exec android-privacy-studio python3 cli.py debloat --execute

restore-pkg:
	docker exec android-privacy-studio python3 cli.py restore --package "$(PKG)" --execute

harden-dry-run:
	docker exec android-privacy-studio python3 cli.py harden --dns "$(DNS)"

harden:
	docker exec android-privacy-studio python3 cli.py harden --dns "$(DNS)" --execute

vbmeta:
	docker exec android-privacy-studio python3 cli.py image vbmeta

overlay:
	docker exec android-privacy-studio python3 cli.py image all

wifi-pair:
	docker exec android-privacy-studio python3 cli.py wifi --endpoint "$(ENDPOINT)" --pair-code "$(CODE)"

wifi-connect:
	docker exec android-privacy-studio python3 cli.py wifi --endpoint "$(ENDPOINT)"

clean-user-apps:
	docker exec android-privacy-studio python3 cli.py clean-user-apps --execute

bundle:
	docker exec android-privacy-studio python3 cli.py bundle --execute

clean-slate: clean-user-apps debloat harden bundle

build-apps:
	docker exec android-privacy-studio python3 cli.py apk --app all

deploy-wall:
	docker exec android-privacy-studio python3 cli.py apk --app firewall --deploy --launch

deploy-audit:
	docker exec android-privacy-studio python3 cli.py apk --app audit --deploy --launch

deploy-scrub:
	docker exec android-privacy-studio python3 cli.py apk --app scrub --deploy --launch

deploy-home:
	docker exec android-privacy-studio python3 cli.py apk --app launcher --deploy --launch

deploy-apps:
	docker exec android-privacy-studio python3 cli.py apk --app all --deploy --launch

monitor:
	docker exec android-privacy-studio python3 cli.py monitor

perf:
	docker exec android-privacy-studio python3 cli.py perf --label "$(or $(LABEL),live)"

perf-history:
	docker exec android-privacy-studio python3 cli.py perf --history

test-jvm:
	docker exec android-privacy-studio python3 scripts/run_jvm_tests.py

test:
	docker exec android-privacy-studio python3 -m unittest discover -s tests -p "test_*.py" -v

test-blocks:
	docker exec android-privacy-studio python3 cli.py test-blocks

qa:
	docker exec android-privacy-studio python3 cli.py qa

audit:
	python3 ~/.gemini/config/scripts/audit_workspace_skills.py --project-dir . --touch --sync-global
