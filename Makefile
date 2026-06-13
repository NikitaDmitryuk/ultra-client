KTLINT_VERSION := 1.5.0
KTLINT         := ./ktlint

.PHONY: setup format lint test clean sing-box-android sing-box-desktop sing-box macos-helper-install macos-helper-uninstall

setup:
	curl -sSLO https://github.com/pinterest/ktlint/releases/download/$(KTLINT_VERSION)/ktlint
	chmod +x ktlint

format: setup
	$(KTLINT) --format "{shared,androidApp,desktopApp}/src/**/*.kt"

lint: setup
	$(KTLINT) --relative "{shared,androidApp,desktopApp}/src/**/*.kt"

test:
	./gradlew :shared:domain:jvmTest :shared:data:jvmTest

clean:
	./gradlew clean
	rm -f ktlint

# Builds androidApp/libs/SingBoxCore.aar from upstream sing-box libbox.
sing-box-android:
	bash sing-box-build/build-android.sh

sing-box-desktop:
	bash sing-box-build/build-desktop.sh

sing-box: sing-box-android sing-box-desktop

macos-helper-install:
	bash scripts/install-macos-helper.sh

macos-helper-uninstall:
	bash scripts/uninstall-macos-helper.sh
