KTLINT_VERSION := 1.5.0
KTLINT         := ./ktlint

.PHONY: setup format lint test clean

setup:
	curl -sSLO https://github.com/pinterest/ktlint/releases/download/$(KTLINT_VERSION)/ktlint
	chmod +x ktlint

format: setup
	$(KTLINT) --format "{shared,androidApp}/**/*.kt"

lint: setup
	$(KTLINT) --relative "{shared,androidApp}/**/*.kt"

test:
	./gradlew :shared:domain:jvmTest :shared:data:jvmTest

clean:
	./gradlew clean
	rm -f ktlint
